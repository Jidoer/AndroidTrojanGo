// Code generated by gobind. DO NOT EDIT.

// Java class clash.Clash is a proxy for talking to a Go program.
//
//   autogenerated by gobind -lang=java github.com/trojan-gfw/igniter-go-libs/clash
package clash;

import go.Seq;

public abstract class Clash {
	static {
		Seq.touch(); // for loading the native library
		_init();
	}
	
	private Clash() {} // uninstantiable
	
	// touch is called from other bound packages to initialize this package
	public static void touch() {}
	
	private static native void _init();
	
	
	
	public static native boolean isRunning();
	public static native void start(String homedir);
	public static native void stop();
}