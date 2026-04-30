use tauri::{
    menu::{Menu, MenuItem},
    tray::{TrayIconBuilder, TrayIconEvent},
    Manager, WindowEvent,
};

#[cfg(windows)]
use windows::Win32::UI::WindowsAndMessaging::{
    HTCAPTION, WM_NCHITTEST,
};
#[cfg(windows)]
use windows::Win32::UI::Shell::{DefSubclassProc, SetWindowSubclass};
#[cfg(windows)]
use windows::Win32::Foundation::{HWND, LPARAM, LRESULT, WPARAM, RECT};

#[cfg(windows)]
unsafe extern "system" fn wnd_proc(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
    _uidsubclass: usize,
    _dwrefdata: usize,
) -> LRESULT {
    if msg == WM_NCHITTEST {
        let x = (lparam.0 & 0xFFFF) as i16 as i32;
        let y = ((lparam.0 >> 16) & 0xFFFF) as i16 as i32;

        let mut rect = RECT::default();
        if windows::Win32::UI::WindowsAndMessaging::GetWindowRect(hwnd, &mut rect).is_ok() {
            // 获取窗口 DPI 缩放比例
            let dpi = windows::Win32::UI::HiDpi::GetDpiForWindow(hwnd);
            let scale_factor = dpi as f32 / 96.0;
            
            // 标题栏高度 28px
            let titlebar_height = (28.0 * scale_factor) as i32;
            // 窗口控制按钮区域约 132px
            let controls_width = (132.0 * scale_factor) as i32;
            
            // 检查鼠标是否在标题栏区域
            if y >= rect.top && y < rect.top + titlebar_height {
                // 排除右侧的窗口控制按钮区域
                if x < rect.right - controls_width {
                    return LRESULT(HTCAPTION as isize);
                }
            }
        }
    }

    DefSubclassProc(hwnd, msg, wparam, lparam)
}

#[tauri::command]
fn greet(name: &str) -> String {
    format!("Hello, {}! You've been greeted from Rust!", name)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            // 创建托盘菜单
            let quit_i = MenuItem::with_id(app, "quit", "退出", true, None::<&str>)?;
            let show_i = MenuItem::with_id(app, "show", "显示主界面", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&show_i, &quit_i])?;

            // 构建托盘
            let _tray = TrayIconBuilder::new()
                .icon(app.default_window_icon().unwrap().clone())
                .menu(&menu)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "quit" => {
                        app.exit(0);
                    }
                    "show" => {
                        if let Some(window) = app.get_webview_window("main") {
                            let _ = window.show();
                            let _ = window.set_focus();
                        }
                    }
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: tauri::tray::MouseButton::Left,
                        ..
                    } = event
                    {
                        let app = tray.app_handle();
                        if let Some(window) = app.get_webview_window("main") {
                            let _ = window.show();
                            let _ = window.set_focus();
                        }
                    }
                })
                .build(app)?;

            #[cfg(windows)]
            {
                if let Some(window) = app.get_webview_window("main") {
                    if let Ok(hwnd_raw) = window.hwnd() {
                        let hwnd = windows::Win32::Foundation::HWND(hwnd_raw.0 as _);
                        unsafe {
                            windows::Win32::UI::Shell::SetWindowSubclass(
                                hwnd,
                                Some(wnd_proc),
                                1, // subclass id
                                0, // ref data
                            );
                        }
                    }
                }
            }

            Ok(())
        })
        .on_window_event(|window, event| match event {
            WindowEvent::CloseRequested { api, .. } => {
                // 拦截关闭请求，改为隐藏窗口
                api.prevent_close();
                window.hide().unwrap();
            }
            _ => {}
        })
        .invoke_handler(tauri::generate_handler![greet])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
