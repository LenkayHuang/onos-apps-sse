/*
 * Copyright 2014 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.sse;

import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServlet;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onlab.osgi.ServiceDirectory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.onosproject.net.Link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web socket servlet capable of creating various sockets for the user interface.
 */
public class SseGuiWebSocketServlet extends WebSocketServlet {

    private static final long PING_DELAY_MS = 5000;
    protected static final Logger log = LoggerFactory.getLogger(SseGuiWebSocketServlet.class);

    private ServiceDirectory directory = new DefaultServiceDirectory();

    private final Set<SseTopologyViewWebSocket> sockets = new HashSet<>();
    private final Timer timer = new Timer();
    private final TimerTask pruner = new Pruner();
    private ScheduledExecutorService executorService;// added by yby

    @Override
    public void init() throws ServletException {
        super.init();

        SseInventory.inventoryInit(directory);
        SseInventory.topologyLinksInit();//hlk
        SseInventory.timeLinkResourceInit();//hlk time-linkresource init
        SseInventory.timeLinkServiceResourceInit();

        timer.schedule(pruner, PING_DELAY_MS, PING_DELAY_MS);
        executorService = Executors.newScheduledThreadPool(20);//added by yby

        Runnable command = new Runnable() {
            public void run(){
                
                ++SseInventory.currentTime;
               
                SseInventory.refreshTimeResource();
                //SseInventory.refreshTimeLinkServiceResource();
                log.info("currentTime is: {}", SseInventory.currentTime);
                if(SseInventory.currentTime >1) {
                    log.info("before refresh resource");
                    SseInventory.timerShowPreview();
                    log.info("currentTime:"+SseInventory.currentTime+"  refresh resource status now");
                }

            }
        };
        executorService.scheduleAtFixedRate(command, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
        SseTopologyViewWebSocket socket = new SseTopologyViewWebSocket(directory, executorService);
        synchronized (sockets) {
            sockets.add(socket);
        }
        return socket;
    }

    // Task for pruning web-sockets that are idle.
    private class Pruner extends TimerTask {
        @Override
        public void run() {
            synchronized (sockets) {
                Iterator<SseTopologyViewWebSocket> it = sockets.iterator();
                while (it.hasNext()) {
                    SseTopologyViewWebSocket socket = it.next();
                    if (socket.isIdle()) {
                        it.remove();
                        socket.close();
                    }
                }
            }
        }
    }
}
