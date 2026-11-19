# Xposed loads this class by name from META-INF/xposed/java_init.list.
-keep class com.blacksoft.miuixpackageupdate.UpdateModule {
    public <init>();
    public void onPackageReady(...);
}
