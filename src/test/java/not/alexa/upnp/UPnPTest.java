package not.alexa.upnp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import not.alexa.upnp.location.PublishedLocationDescriptor;

public class UPnPTest {

    @Test
    public void testScheme() {
        assertEquals("urn:schemas-upnp-org:device:notalexa-upnptest:1",UPnP.getDefaultDeviceURN("notalexa-upnptest", 1));
    }

    @Test
    public void turnAround() {
        CountDownLatch latch=new CountDownLatch(1);
        try(UPnP upnp=new UPnP().setTTL(20).setMX(2).setHttpPort(49999).start()) {
            UPnPScanner scanner=upnp.startScan(new UPnPMessage(null,"urn:schemas-upnp-org:device:notalexa-upnptest:1",null),new UPnPCallback() {
                int count=1;
                @Override
                public void onMessageReceived(UPnPScanner scanner,InetAddress from,boolean reply,int searchId, UPnPMessage msg) {
                    System.out.println(from+"[reply="+reply+"] Received "+msg);
                    try {
                        System.out.write(msg.getLocation().getContent());
                    } catch(Throwable t) {
                        t.printStackTrace();
                    }
                }

                @Override
                public void onMessageByeBye(UPnPScanner scanner,InetAddress from, UPnPMessage msg) {
                    System.out.println(from+"[byebye] Received "+msg);
                }

                @Override
                public void onSearchTimedOut(UPnPScanner scanner,int searchId) {
                    if(count<5) {
                        count++;
                        System.out.println("Start search #"+count);
                        scanner.search(searchId);
                    } else {
                        scanner.getServer().schedule(() -> {
                            latch.countDown();
                        }, 10,TimeUnit.SECONDS); 
                    }
                }
                
            });
            UPnPMessage msg=new UPnPMessage(UUID.randomUUID().toString(),UPnP.getDefaultDeviceURN("notalexa-upnptest",1), new PublishedLocationDescriptor("description.xml"));
            upnp.publish(msg);
            scanner.search(5);
            latch.await();
        } catch(Throwable t) {
            t.printStackTrace();
        }
    }
    
    public static void main(String[] args) throws Throwable {
        new UPnPTest().turnAround();
    }
}
