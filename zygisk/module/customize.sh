SKIPUNZIP=0

ui_print "************************************"
ui_print "       转基因反应堆 (Reactor)       "
ui_print "   Zygisk Native Module v1.0.0      "
ui_print "************************************"

# Install bundled Controller App if present
if [ -f "$MODPATH/reactor.apk" ]; then
    ui_print "- 正在安装转基因反应堆控制端 App..."
    pm install -r "$MODPATH/reactor.apk" >/dev/null 2>&1 || pm install "$MODPATH/reactor.apk" >/dev/null 2>&1 || true
    ui_print "- 控制端 App 安装完成"
fi

ui_print "- 模块安装完成，请重启设备使 Zygisk 生效！"
