use adblock::{
    lists::{FilterSet, ParseOptions},
    request::Request,
    resources::{PermissionMask, Resource},
    Engine,
};
use jni::{
    objects::{JClass, JObjectArray, JString},
    sys::{jboolean, jlong, jstring},
    JNIEnv,
};
use std::{
    ffi::{c_char, c_void, CStr, CString},
    sync::mpsc::{self, Sender},
    thread,
};

enum EngineCommand {
    Check {
        url: String,
        source_url: String,
        resource_type: String,
        reply: Sender<bool>,
    },
    Cosmetics {
        url: String,
        reply: Sender<String>,
    },
    GenericSelectors {
        url: String,
        classes: Vec<String>,
        ids: Vec<String>,
        reply: Sender<String>,
    },
}

/// Opaque context passed through the stable C ABI.
struct EngineHandle {
    requests: Sender<EngineCommand>,
}

/// Creates an engine from bundled starter rules plus external rules and resources.
///
/// `filter_rules` accepts plain list text or a JSON array of per-list objects containing
/// `filter_rules` (or `rules`) and an optional `permission_mask`. `resources_json` accepts the
/// serialized `Vec<adblock::resources::Resource>` format produced by Brave's resource package.
#[no_mangle]
pub extern "C" fn bravetv_adblock_create_engine(
    filter_rules: *const c_char,
    resources_json: *const c_char,
) -> *mut c_void {
    let external_rules = read_c_string(filter_rules);
    let resources_text = read_c_string(resources_json);
    let (requests, receiver) = mpsc::channel::<EngineCommand>();

    thread::spawn(move || {
        let mut filters = FilterSet::new(false);
        filters.add_filter_list(
            "||doubleclick.net^\n||googlesyndication.com^\n||googleadservices.com^\n||adnxs.com^\n||amazon-adsystem.com^\n||outbrain.com^\n||taboola.com^".to_owned(),
            ParseOptions::default(),
        );
        add_external_filter_rules(&mut filters, &external_rules);
        let mut engine = Engine::new_with_filter_set(filters);
        engine.use_resources(parse_resources(&resources_text));

        while let Ok(command) = receiver.recv() {
            match command {
                EngineCommand::Check {
                    url,
                    source_url,
                    resource_type,
                    reply,
                } => {
                    let request_type = normalize_resource_type(&resource_type);
                    let blocked = Request::new(&url, &source_url, request_type, "GET")
                        .map(|request| engine.check_network_request(&request).should_block())
                        .unwrap_or(false);
                    let _ = reply.send(blocked);
                }
                EngineCommand::Cosmetics { url, reply } => {
                    let resources = engine.url_cosmetic_resources(&url);
                    let mut selectors = resources.hide_selectors.into_iter().collect::<Vec<_>>();
                    selectors.sort_unstable();
                    let css = selectors
                        .into_iter()
                        .map(|selector| format!("{selector}{{display:none!important;}}"))
                        .collect::<String>();
                    let css_literal =
                        serde_json::to_string(&css).unwrap_or_else(|_| "\"\"".to_owned());
                    let javascript = format!(
                        "(()=>{{const s=document.createElement('style');s.textContent={css_literal};(document.head||document.documentElement).appendChild(s)}})();\n{}",
                        resources.injected_script
                    );
                    let _ = reply.send(javascript);
                }
                EngineCommand::GenericSelectors {
                    url,
                    classes,
                    ids,
                    reply,
                } => {
                    let resources = engine.url_cosmetic_resources(&url);
                    let selectors = if resources.generichide {
                        Vec::new()
                    } else {
                        engine.hidden_class_id_selectors(classes, ids, &resources.exceptions)
                    };
                    let _ = reply.send(
                        serde_json::to_string(&selectors).unwrap_or_else(|_| "[]".to_owned()),
                    );
                }
            }
        }
    });

    Box::into_raw(Box::new(EngineHandle { requests })) as *mut c_void
}

fn read_c_string(pointer: *const c_char) -> String {
    if pointer.is_null() {
        return String::new();
    }
    unsafe { CStr::from_ptr(pointer) }
        .to_str()
        .unwrap_or_default()
        .to_owned()
}

fn normalize_resource_type(resource_type: &str) -> &'static str {
    match resource_type.to_ascii_lowercase().as_str() {
        "script" => "script",
        "xmlhttprequest" | "xhr" | "fetch" => "xmlhttprequest",
        "image" | "imageset" => "image",
        "media" | "audio" | "video" => "media",
        "stylesheet" | "style" => "stylesheet",
        "font" => "font",
        "document" | "main_frame" => "document",
        "subdocument" | "sub_frame" | "iframe" => "subdocument",
        "object" => "object",
        "ping" | "beacon" => "ping",
        _ => "other",
    }
}

fn add_external_filter_rules(filters: &mut FilterSet, text: &str) {
    if text.trim().is_empty() {
        return;
    }
    if let Ok(value) = serde_json::from_str::<serde_json::Value>(text) {
        let entries = value.as_array().or_else(|| {
            value
                .get("filter_lists")
                .and_then(serde_json::Value::as_array)
        });
        if let Some(entries) = entries {
            for entry in entries {
                let (rules, permission_mask) = if let Some(rules) = entry.as_str() {
                    (Some(rules), 0)
                } else {
                    (
                        entry
                            .get("filter_rules")
                            .or_else(|| entry.get("rules"))
                            .and_then(serde_json::Value::as_str),
                        entry
                            .get("permission_mask")
                            .and_then(serde_json::Value::as_u64)
                            .unwrap_or(0) as u8,
                    )
                };
                if let Some(rules) = rules {
                    filters.add_filter_list(
                        rules.to_owned(),
                        ParseOptions {
                            permissions: PermissionMask::from_bits(permission_mask),
                            ..ParseOptions::default()
                        },
                    );
                }
            }
            return;
        }
    }
    filters.add_filter_list(text.to_owned(), ParseOptions::default());
}

fn parse_resources(text: &str) -> Vec<Resource> {
    if text.trim().is_empty() {
        return Vec::new();
    }
    if let Ok(resources) = serde_json::from_str::<Vec<Resource>>(text) {
        return resources;
    }
    serde_json::from_str::<serde_json::Value>(text)
        .ok()
        .and_then(|value| value.get("resources").cloned())
        .and_then(|value| serde_json::from_value::<Vec<Resource>>(value).ok())
        .unwrap_or_default()
}

fn java_string_array(env: &mut JNIEnv, array: &JObjectArray) -> Vec<String> {
    let Ok(length) = env.get_array_length(array) else {
        return Vec::new();
    };
    (0..length.min(2_000))
        .filter_map(|index| {
            let object = env.get_object_array_element(array, index).ok()?;
            let value: String = env.get_string(&JString::from(object)).ok()?.into();
            Some(value)
        })
        .collect()
}

/// Checks a request using its URL, source page URL, and adblock resource type.
/// Returns 1 when blocked, otherwise 0.
///
/// # Safety
/// `handle` must be a live handle returned by `bravetv_adblock_create_engine`; all strings
/// must point to valid NUL-terminated UTF-8 strings.
#[no_mangle]
pub unsafe extern "C" fn bravetv_adblock_should_block(
    handle: *mut c_void,
    url: *const c_char,
    source_url: *const c_char,
    resource_type: *const c_char,
) -> i32 {
    if handle.is_null() || url.is_null() || source_url.is_null() || resource_type.is_null() {
        return 0;
    }
    let (Ok(url), Ok(source_url), Ok(resource_type)) = (
        CStr::from_ptr(url).to_str(),
        CStr::from_ptr(source_url).to_str(),
        CStr::from_ptr(resource_type).to_str(),
    ) else {
        return 0;
    };

    let engine_handle = &*(handle as *const EngineHandle);
    let (reply_tx, reply_rx) = mpsc::channel();
    if engine_handle
        .requests
        .send(EngineCommand::Check {
            url: url.to_owned(),
            source_url: source_url.to_owned(),
            resource_type: resource_type.to_owned(),
            reply: reply_tx,
        })
        .is_err()
    {
        return 0;
    }
    i32::from(reply_rx.recv().unwrap_or(false))
}

/// Returns page-specific cosmetic CSS and scriptlets as executable JavaScript.
#[no_mangle]
pub extern "system" fn Java_com_bravetv_app_BraveEngine_getCosmeticResources(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    page_url: JString,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let Ok(page_url) = env.get_string(&page_url).map(String::from) else {
        return std::ptr::null_mut();
    };
    let engine_handle = unsafe { &*(handle as *const EngineHandle) };
    let (reply_tx, reply_rx) = mpsc::channel();
    if engine_handle
        .requests
        .send(EngineCommand::Cosmetics {
            url: page_url,
            reply: reply_tx,
        })
        .is_err()
    {
        return std::ptr::null_mut();
    }
    match reply_rx
        .recv()
        .ok()
        .and_then(|javascript| env.new_string(javascript).ok())
    {
        Some(value) => value.into_raw(),
        None => std::ptr::null_mut(),
    }
}

/// Returns applicable generic selectors for the classes and IDs currently on a page.
#[no_mangle]
pub extern "system" fn Java_com_bravetv_app_BraveEngine_getGenericCosmeticSelectors(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    page_url: JString,
    classes: JObjectArray,
    ids: JObjectArray,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let Ok(page_url) = env.get_string(&page_url).map(String::from) else {
        return std::ptr::null_mut();
    };
    let classes = java_string_array(&mut env, &classes);
    let ids = java_string_array(&mut env, &ids);
    let engine_handle = unsafe { &*(handle as *const EngineHandle) };
    let (reply_tx, reply_rx) = mpsc::channel();
    if engine_handle
        .requests
        .send(EngineCommand::GenericSelectors {
            url: page_url,
            classes,
            ids,
            reply: reply_tx,
        })
        .is_err()
    {
        return std::ptr::null_mut();
    }
    match reply_rx
        .recv()
        .ok()
        .and_then(|json| env.new_string(json).ok())
    {
        Some(value) => value.into_raw(),
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_bravetv_app_BraveEngine_createEngine(
    mut env: JNIEnv,
    _class: JClass,
    filter_rules: JString,
    resources_json: JString,
) -> jlong {
    let rules: String = env
        .get_string(&filter_rules)
        .map(|value| value.into())
        .unwrap_or_default();
    let resources: String = env
        .get_string(&resources_json)
        .map(|value| value.into())
        .unwrap_or_default();
    let rules = CString::new(rules).unwrap_or_default();
    let resources = CString::new(resources).unwrap_or_default();
    bravetv_adblock_create_engine(rules.as_ptr(), resources.as_ptr()) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_bravetv_app_BraveEngine_shouldBlock(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    url: JString,
    source_url: JString,
    resource_type: JString,
) -> jboolean {
    if handle == 0 {
        return 0;
    }
    let (Ok(url), Ok(source_url), Ok(resource_type)) = (
        env.get_string(&url).map(String::from),
        env.get_string(&source_url).map(String::from),
        env.get_string(&resource_type).map(String::from),
    ) else {
        return 0;
    };
    let (Ok(url), Ok(source_url), Ok(resource_type)) = (
        CString::new(url),
        CString::new(source_url),
        CString::new(resource_type),
    ) else {
        return 0;
    };
    unsafe {
        bravetv_adblock_should_block(
            handle as *mut c_void,
            url.as_ptr(),
            source_url.as_ptr(),
            resource_type.as_ptr(),
        ) as jboolean
    }
}
