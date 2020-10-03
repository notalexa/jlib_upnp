# UPnP Discovery and Description Library

This projects provide [UPnP discovery and description](https://en.wikipedia.org/wiki/Universal_Plug_and_Play) functionality. Currently in version 1.0.1, the library can be included as a maven artifact from the not Alexa repository:

<table style="padding-left:50px;width:100%">
<tr><td>Repository</td><td><code>https://maven.pkg.github.com/notalexa/repo</code></td><tr>
<tr><td>GroupId</td><td><code>not.alexa</code></td><tr>
<tr><td>ArtifactId</td><td><code>jlib-upnp</code></td><tr>
<tr><td>Version</td><td><code>1.0.1</code></td><tr>
</table>

## Description

Currently, the library is restricted to [UPnP 1.0](http://upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.0.pdf) and IPv4 multicast. The library can be used both on the client side to discover devices and on the server side to publish devices.
For the second use case, the library includes a very simple HTTP server which is started
automatically if the port is specified. Note that the server is really simple. A connection
is used once and no TLS is provided. The server is intended to deliver just the device
descriptions published and assumes living in a local network.

<p>The following parameters are configurable:

<p><table style="padding-left:50px;width:100%">
<tr><td>Multicast Address</td><td>The multicast address used for sending and receiving messages.</td></tr>
<tr><td>Multicast Port</td><td>The multicast port used for sending and receiving messages.</td></tr>
<tr><td>HTTP Port</td><td>The TCP port used for delivering descriptions via HTTP. (Server only)</td></tr>
<tr><td>Time To Live</td><td>The validity of an UPnP Message in seconds. This parameter defaults to 300 (seconds) and the server broadcasts the message again before the time expires. (Server only)</td></tr>
<tr><td>MX</td><td>Seconds to delay responds. This parameter defaults to 5 (seconds). Set by the client, the server chooses a random delay within this interval to wait for a respond. (Client only)</td></tr>
<tr><td>Bye Bye on shutdown</td><td>if<code>true</code>, the server sends a bye bye message for each published device on shutdown. (Server only)</td></tr>
<table>

<p>When run in server mode, the library allows multiple methods to resolve the content of the device descriptor:

* as a constant
* via an URL
* via a classloader resource stream

## Examples

The following code snippet asks for a device with URN `urn:schemas-upnp-org:device:notalexa-butler:1` overriding the default MX value by 2 (seconds):

	CountDownLatch waitLatch=new CountDownLatch(1);
	try(UPnP upnp=new UPnP().setMX(2).start()) {
		UPnPScanner scanner=upnp.startScan(new UPnPMessage(null,UPnP.getDefaultDeviceURN("notalexa-butler",1),null),new UPnPCallback() {
			@Override
			public void onMessageReceived(UPnPScanner scanner,InetAddress from,boolean reply,int searchId, UPnPMessage msg) {
				registerButler(msg);
				waitLatch.countDown();
			}
	
			@Override
			public void onSearchTimedOut(UPnPScanner scanner,int searchId) {
				waitLatch.countDown();
			}
		}
		scanner.search(0);
		waitLatch.await();
	}
 
Assume that the file `butler.xml` can be resolved as a classloader resource. The following snippet publish the butler service with a random UUID using a http server listening at port 8080:

	try(UPnP upnp=new UPnP().setHttpPort(8080).start()) {
		UPnPMessage msg=new UPnPMessage(UUID.randomUUID().toString(),UPnP.getDefaultDeviceURN("notalexa-butler",1), new ClassLoaderLocationDescriptor("butler.xml"));
		upnp.publish(msg);
		shutdownLatch.await();
	}

