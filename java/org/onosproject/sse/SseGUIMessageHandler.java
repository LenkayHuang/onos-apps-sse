package org.onosproject.sse;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.onosproject.core.CoreService;
import org.onosproject.core.ApplicationId;

import com.google.common.collect.Lists;
import org.onosproject.net.topology.PathService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.topology.LinkWeight;
import org.onosproject.net.topology.TopologyEdge;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.onosproject.net.resource.ResourceAllocation;
import org.onosproject.net.resource.link.LambdaResource;
import org.onosproject.net.resource.link.LinkResourceService;
import org.onosproject.net.resource.ResourceRequest;
import org.onosproject.net.resource.link.LinkResourceAllocations;
import org.onosproject.net.resource.link.LambdaResourceRequest;
import org.onosproject.net.resource.link.LambdaResourceAllocation;
import org.onosproject.net.resource.link.LinkResourceRequest;
import org.onosproject.net.resource.link.DefaultLinkResourceRequest;
import org.onosproject.net.resource.link.DefaultLinkResourceAllocations;

import org.onosproject.net.DefaultPath;
import org.onosproject.net.DefaultLink;

import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentId;
import org.onosproject.net.intent.IntentState;
//import org.onosproject.net.intent.IntentOperations;
import org.onosproject.net.intent.HostToHostIntent;
import org.onosproject.net.intent.OpticalConnectivityIntent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.OpticalTimeConnectivityIntent;

import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;


import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.ElementId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;

import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.lang.InterruptedException;


import org.onlab.osgi.ServiceDirectory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import org.onosproject.sse.SseTimerType;


public class SseGUIMessageHandler {

	protected static final Logger log = LoggerFactory.getLogger(SseGUIMessageHandler.class);

	private static final String APP_ID = "org.onosproject.sse";

    private static final String COMPACT = "%s/%s-%s/%s";

    protected final ObjectMapper mapper = new ObjectMapper();

    private IntentService intentService;

    private Map<Integer, Map<Link, Map<Integer, Set<LambdaResource>>>> scheduledResource;

    protected PathService pathService;

    private SseTopologyViewWebSocket websocket;

    protected LinkResourceService resourceService;//hlk

    private ScheduledExecutorService executorService;// added by yby

    private final ApplicationId appId;

    private ConnectPoint srcNode;

    private ConnectPoint dstNode;

    protected final Map<ConnectPoint, OpticalConnectivityIntent> inStatusTportMap =
            new ConcurrentHashMap<>();
    protected final Map<ConnectPoint, OpticalConnectivityIntent> outStatusTportMap =
            new ConcurrentHashMap<>();
    protected final Map<ConnectPoint, Map<ConnectPoint, Intent>> intentMap =
            new ConcurrentHashMap<>();

    private Set<LambdaResource> targetLambda=new HashSet<LambdaResource>();

    // Retrieves the payload from the specified event.
    protected ObjectNode payload(ObjectNode event) {
        return (ObjectNode) event.path("payload");
    }

    // Returns the specified node property as a number
    protected long number(ObjectNode node, String name) {
        return node.path(name).asLong();
    }

    // Returns the specified node property as a string.
    protected String string(ObjectNode node, String name) {
        return node.path(name).asText();
    }

    private static String compactLinkString(Link link) {
        return String.format(COMPACT, link.src().elementId(), link.src().port(),
                             link.dst().elementId(), link.dst().port());
    }

    protected ObjectNode envelope(String type, long sid, ObjectNode payload) {
        ObjectNode event = mapper.createObjectNode();
        event.put("event", type);
        if (sid > 0) {
            event.put("sid", sid);
        }
        event.set("payload", payload);
        return event;
    }

/*    protected Long dateToLong(String date){
        String[] dateArray1=date.split(" ");
        String[] dateArray=dateArray1[0].split("-");
        String tempDate = "";
        for(int i=0; i< dateArray.length; i++){
            tempDate = tempDate + dateArray[i];
        }
        return Long.parseLong(tempDate);
    }

    protected Long timeToLong(String time) {
        String[] timeArray1=time.split(" ");
        String[] timeArray=timeArray1[1].split(":");
        String tempTime = "";
        for(int i=0; i< timeArray.length; i++){//remove the second field
            tempTime = tempTime + timeArray[i];
        }
        return Long.parseLong(tempTime);
    }

    protected Long dateToLong2(String date){
        String[] dateArray=date.split("-");
        String tempDate = "";
        for(int i=0; i< dateArray.length; i++){
            tempDate = tempDate + dateArray[i];
        }
        return Long.parseLong(tempDate);
    }

    protected Long timeToLong2(String time) {
        String[] timeArray=time.split(":");
        String tempTime = "";
        for(int i=0; i< timeArray.length; i++){//remove the second field
            tempTime = tempTime + timeArray[i];
        }
        return Long.parseLong(tempTime);
    }


    protected long timeTranlator (long date, long time) {

        // 20150610 - 20150520
        long dateNum = (date/100-SseInventory.initDate/100)*30+(date%100-SseInventory.initDate%100);
        long timeNum = (time/100-SseInventory.initTime/100)*4+(time%100-SseInventory.initTime%100)/15;//HLK 5.20 need to be modified

        long changedTime = dateNum*96+timeNum;
        return changedTime;
    }
    // Returns the specified node property as a string.
    protected String string(ObjectNode node, String name, String defaultValue) {
        return node.path(name).asText(defaultValue);
    }
*/

    public SseGUIMessageHandler(ServiceDirectory directory, ScheduledExecutorService executorService, SseTopologyViewWebSocket websocket) {
        //super(directory);
        //intentFilter = new TopologyViewIntentFilter(intentService, deviceService,
                                                    //hostService, linkService);
        resourceService = directory.get(LinkResourceService.class);
        pathService = directory.get(PathService.class);
        intentService = directory.get(IntentService.class);
        appId = directory.get(CoreService.class).registerApplication(APP_ID);
        this.executorService = executorService;
        this.websocket = websocket;
    }

    public void baseTimeReset(ObjectNode event){
        /*ObjectNode payload = payload(event);
        SseInventory.initDate=dateToLong2(string(payload, "baseDate"));
        SseInventory.initTime=timeToLong2(string(payload, "baseTime"));*/
        SseInventory.currentTime=0;
        SseInventory.startFlag=1;
    }


	public OpticalTimeConnectivityIntent sseOpticalPathHandler (ObjectNode event) {
        /*OpticalTimeConnectivityIntent returnIntent=handleOneARRequest (event);
        if(returnIntent==null){
            reSchedule();
        }*/
        ObjectNode payload = payload(event);
        int serviceId = Integer.parseInt(string(payload,"serviceid"));
        if(serviceId==1){
            SseInventory.statisticsStartTime=SseInventory.currentTime+10;
        }
        else if(serviceId==999){
            SseInventory.statisticsEndTime=SseInventory.currentTime;
            log.info("serviceId: {}", serviceId);
            log.info("failedServiceNum: {}", SseInventory.failedServiceNum);   
        }

        OpticalTimeConnectivityIntent returnIntent=handleOneARRequest (event);
        if(returnIntent==null){
            try{
                reSchedule();
            }
            catch(Exception e){
                log.error("reSchedule Error");
            }
            returnIntent=handleOneARRequest(event);
            if(returnIntent==null){
                websocket.sendStateMessage("No Target Resource!", serviceId);//hlk 5.26
                SseInventory.failedServiceNum++;
            }
            return returnIntent;
        }
        else{
            return returnIntent;
        }

	}
    public OpticalTimeConnectivityIntent handleOneARRequest (ObjectNode event) {
        ObjectNode payload = payload(event);
        log.info("start parse Service id {}");
        int serviceId = Integer.parseInt(string(payload,"serviceid"));
        log.info("start process the service with id {}", serviceId);
        SseInventory.lastService=serviceId;
        /*if(serviceId==1){
            SseInventory.statisticsStartFlag=true;
        }*/

        /*if(serviceId==100){
            double freeResourceRatio=0;
            long statisticNum=SseInventory.statisticTimeSlot.size();
            for(Map.Entry<Long,Long> entry:SseInventory.statisticTimeSlot.entrySet()){
                freeResourceRatio=freeResourceRatio+(double)(entry.getValue()/(9*40));
            }
            freeResourceRatio=freeResourceRatio/statisticNum;
            log.info("freeResourceRatio :",freeResourceRatio);
            log.info("failedServiceNum: ", SseInventory.failedServiceNum);
        }*/

        Path targetForwardPath = null;
        Path targetReversePath = null;
        LinkResourceAllocations forwardLambdaResource = null;
        LinkResourceAllocations reverseLambdaResource = null;

        log.info("HLK 6.2 ----before srcNode dstNode");
        ConnectPoint srcNode = new ConnectPoint(DeviceId.deviceId(string(payload, "srcele")),PortNumber.portNumber(number(payload,"srcport")));
        ConnectPoint dstNode = new ConnectPoint(DeviceId.deviceId(string(payload, "dstele")),PortNumber.portNumber(number(payload,"dstport")));
        
        log.info("HLK 6.2 ----before txtdata");
        int requestWavelengthSlots = Integer.parseInt(string(payload,"txtdata")); //required bandwidth indeed.

        int reMark = Integer.parseInt(string(payload,"remark"));
        int earlyStartTime = 0;
        int actualStartTime = 0;
        int actualEndTime = 0;
        int deadLineTime = 0;

        if(reMark == -1) {
            earlyStartTime=Integer.parseInt(string(payload,"erStartDate"));
            actualStartTime=Integer.parseInt(string(payload,"exStartDate"));
            actualEndTime=Integer.parseInt(string(payload,"exEndDate"));
            deadLineTime=Integer.parseInt(string(payload,"dlEndDate"));
        }
        else {
            earlyStartTime=Integer.parseInt(string(payload,"erStartDate"))+SseInventory.currentTime;
            actualStartTime=Integer.parseInt(string(payload,"exStartDate"))+SseInventory.currentTime;
            actualEndTime=Integer.parseInt(string(payload,"exEndDate"))+SseInventory.currentTime;
            deadLineTime=Integer.parseInt(string(payload,"dlEndDate"))+SseInventory.currentTime;
        }

        Set<ResourceRequest> requestResource = new HashSet<ResourceRequest>();
        for(int i=0; i<requestWavelengthSlots; i++) {
            requestResource.add(new LambdaResourceRequest());
        }
        log.info("HLK 11.2 ----before calculate");
        Set<Path> forwardPaths = calculateOpticalPath(srcNode, dstNode);//hlk get path

        if(forwardPaths.isEmpty()){
            log.info("no available paths, TO DO: reject this request");
            websocket.sendStateMessage("No Available Path!", serviceId);//hlk 5.26
        }
        Iterator<Path> forwardPathItr = forwardPaths.iterator();
        //Iterator<Path> reversePathItr = reversePaths.iterator();
        while(forwardPathItr.hasNext()) {

            Path forwardPathItem=forwardPathItr.next();

            LinkResourceAllocations tmpForwardLambdaResource = assignWavelength(serviceId, actualStartTime - SseInventory.currentTime, actualEndTime - SseInventory.currentTime,forwardPathItem, requestResource);

            if (tmpForwardLambdaResource == null) {
                forwardLambdaResource = null;
                //reverseLambdaResource =null;
                continue;
            }
            else {
                List<Link> reverseLinkList = new ArrayList<Link>();
                reverseLinkList.addAll(forwardPathItem.links());
                Collections.reverse(reverseLinkList);
                List<Link> rebuiltReverseLinkList = new ArrayList<Link>();

                for(Link singleLink : reverseLinkList) {
                    Link tempLink = new DefaultLink(singleLink.providerId(), singleLink.dst(), singleLink.src(), singleLink.type(), singleLink.state(), singleLink.isDurable(), singleLink.annotations());
                    //log.info("HLK 6.5----templink {}  singleLink {}", tempLink, singleLink);
                    rebuiltReverseLinkList.add(tempLink);
                }
                Path reversePathItem = new DefaultPath(forwardPathItem.providerId(), rebuiltReverseLinkList, forwardPathItem.cost(), forwardPathItem.annotations());

                LinkResourceAllocations tmpReverseLambdaResource = buildResourceAllocations(serviceId, actualStartTime - SseInventory.currentTime, actualEndTime - SseInventory.currentTime,reversePathItem, requestResource, targetLambda);
                targetForwardPath = forwardPathItem;
                targetReversePath = reversePathItem;
                forwardLambdaResource = tmpForwardLambdaResource;
                reverseLambdaResource = tmpReverseLambdaResource;
                break;

            }
        }

        log.info("HLK 11.2-----after path itr");
        //TO DO, print the target path with corresponding resources, for Test
        if(targetForwardPath == null||targetReversePath==null||forwardLambdaResource==null||reverseLambdaResource==null) {
            //to do, reject this request.
            //log.info("HLK targetResource is NULL");
            return null;
        }
        else{

            Set<LambdaResource> lambdasForThisService = new HashSet<LambdaResource>();
            lambdasForThisService.addAll(targetLambda);
            
            // new parameter for storing service 
            log.info("6.4-----1");
            SseOpticalPathParameter serviceForwardPathParameter = new SseOpticalPathParameter(serviceId, earlyStartTime, actualStartTime, actualEndTime, 
                                                        deadLineTime, srcNode, dstNode, targetForwardPath,targetReversePath, forwardLambdaResource, reverseLambdaResource, lambdasForThisService);
            SseOpticalPathParameter serviceReversePathParameter = new SseOpticalPathParameter(serviceId, earlyStartTime, actualStartTime, actualEndTime, 
                                                        deadLineTime, dstNode, srcNode, targetReversePath, targetForwardPath, reverseLambdaResource,forwardLambdaResource, lambdasForThisService);            
            log.info("HLK 11.2 appid {}, src {}, dst{}, alloc{}, path{}, serviceId{} ",appId, srcNode, dstNode, forwardLambdaResource, targetForwardPath, serviceId);
            OpticalTimeConnectivityIntent optForwardConnectivityIntent = new OpticalTimeConnectivityIntent(appId, srcNode, dstNode, forwardLambdaResource, targetForwardPath, serviceId);
            log.info("HLK 11.2 appid {}, src {}, dst{}, alloc{}, path{}, serviceId{} ",appId, dstNode, srcNode, reverseLambdaResource, targetReversePath, serviceId);
            OpticalTimeConnectivityIntent optReverseConnectivityIntent = new OpticalTimeConnectivityIntent(appId, dstNode, srcNode, reverseLambdaResource, targetReversePath, serviceId);
            log.info("6.4------2  1");
            SseInventory.markServiceToResourceMap(serviceId,(actualStartTime - SseInventory.currentTime) , (actualEndTime - SseInventory.currentTime), targetForwardPath,lambdasForThisService);
            SseInventory.markServiceToResourceMap(serviceId,(actualStartTime - SseInventory.currentTime) , (actualEndTime - SseInventory.currentTime), targetReversePath,lambdasForThisService);
            log.info("6.4------2  2");
            SseInventory.resourceReservation((actualStartTime - SseInventory.currentTime) , (actualEndTime - SseInventory.currentTime), targetForwardPath, lambdasForThisService);
            SseInventory.resourceReservation((actualStartTime - SseInventory.currentTime) , (actualEndTime - SseInventory.currentTime), targetReversePath, lambdasForThisService);

            log.info("6.4-----3");
            //log.info("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@  actualStartTime - SseInventory.currentTime is: {}",  actualStartTime - SseInventory.currentTime);
            InternalCallableTimer addForwardIntentTimer = new InternalCallableTimer(SseTimerType.ADD_INSTALL_INTENT_TIMER, optForwardConnectivityIntent, intentService, websocket);
            InternalCallableTimer addReverseIntentTimer = new InternalCallableTimer(SseTimerType.ADD_INSTALL_INTENT_TIMER, optReverseConnectivityIntent, intentService, websocket);
            InternalCallableTimer withdrawForwardIntentTimer = new InternalCallableTimer(SseTimerType.ADD_WITHDRAW_INTENT_TIMER, optForwardConnectivityIntent, intentService, websocket);
            InternalCallableTimer withdrawReverseIntentTimer = new InternalCallableTimer(SseTimerType.ADD_WITHDRAW_INTENT_TIMER, optReverseConnectivityIntent, intentService, websocket);
            //log.info("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@  system time {}",SseInventory.currentTime);
            log.info("6.4-----4");
            ScheduledFuture<IntentId> addForwardFuture = executorService.schedule(addForwardIntentTimer, 10*(actualStartTime - SseInventory.currentTime), TimeUnit.SECONDS);
            ScheduledFuture<IntentId> addReverseFuture = executorService.schedule(addReverseIntentTimer, 10*(actualStartTime - SseInventory.currentTime), TimeUnit.SECONDS);
            ScheduledFuture<IntentId> withdrawForwardFuture = executorService.schedule(withdrawForwardIntentTimer, 10*(actualEndTime - SseInventory.currentTime), TimeUnit.SECONDS);
            ScheduledFuture<IntentId> withdrawReverseFuture = executorService.schedule(withdrawReverseIntentTimer, 10*(actualEndTime - SseInventory.currentTime), TimeUnit.SECONDS);
            log.info("6.4-----5");
            SseInventory.futureAddList.put(optForwardConnectivityIntent.id(), addForwardFuture);
            SseInventory.futureAddList.put(optReverseConnectivityIntent.id(), addReverseFuture);
            SseInventory.futureWithdrawList.put(optForwardConnectivityIntent.id(), withdrawForwardFuture);
            SseInventory.futureWithdrawList.put(optReverseConnectivityIntent.id(), withdrawReverseFuture);
            log.info("6.4-----6");
            SseInventory.addScheduledService(serviceId, optForwardConnectivityIntent, optReverseConnectivityIntent, serviceForwardPathParameter, serviceReversePathParameter);
            log.info("6.4-----before Return");
            // TO DO 
            //SseInventory.addTimeLinkServiceResouce(serviceId,serviceForwardPathParameter);
            websocket.sendStateMessage("Path has been successfully reserved!", serviceId);//hlk 5.26

            //log.info("@@@@@@@@@@@@@@@@@@@@service process completed, id is {}", serviceId);
            return optForwardConnectivityIntent;
        }

    }



    public void sseDeleteCurrentService(ObjectNode event){
        try{
            ObjectNode payload = payload(event);//hlk 5.23
            int serviceId = Integer.parseInt(string(payload,"serviceid"));
            OpticalTimeConnectivityIntent forwardIntent = SseInventory.currentForwardServiceIntents.get(serviceId);
            OpticalTimeConnectivityIntent reverseIntent = SseInventory.currentReverseServiceIntents.get(serviceId);
            SseInventory.futureAddList.remove(forwardIntent.id());
            SseInventory.futureAddList.remove(reverseIntent.id());
            SseInventory.futureWithdrawList.remove(forwardIntent.id());
            SseInventory.futureWithdrawList.remove(reverseIntent.id());
            intentService.withdraw(forwardIntent); 
            intentService.withdraw(reverseIntent); 
            SseInventory.releaseScheduledServiceResource(SseInventory.scheduledForwardServices.get(serviceId));
            SseInventory.releaseScheduledServiceResource(SseInventory.scheduledReverseServices.get(serviceId));
            SseInventory.deMarkServiceToResourceMap(SseInventory.scheduledForwardServices.get(serviceId));
            SseInventory.deMarkServiceToResourceMap(SseInventory.scheduledReverseServices.get(serviceId));
            SseInventory.delCurrentService(serviceId);
            // TO DO, release the remaining resource

        }catch(Exception e){

        }
        
    }

    public void sseDeleteScheduledService(ObjectNode event){
        try{
            //log.info("%%%%%%%%%%%%%%%%%%%%%%%% before parse serviceId in sseDeleteScheduledService.");
            //log.info("go into sseDeleteScheduledService");
            ObjectNode payload = payload(event);//hlk 5.23
            int serviceId = Integer.parseInt(string(payload,"serviceid"));
            //SseInventory.scheduledServicesPara.remove(serviceId);//remove from ParaMap
            OpticalTimeConnectivityIntent forwardIntent = SseInventory.scheduledForwardServiceIntents.get(serviceId);
            OpticalTimeConnectivityIntent reverseIntent = SseInventory.scheduledReverseServiceIntents.get(serviceId);
            //log.info("5.23 the info of corresponding intent is {}",intent);
            //log.info("intentId is : {}", forwardIntent.id());
            InternalCallableTimer forwardTimer = new InternalCallableTimer(SseTimerType.CANCEL_INTENT_TIMER, forwardIntent, intentService,websocket);
            InternalCallableTimer reverseTimer = new InternalCallableTimer(SseTimerType.CANCEL_INTENT_TIMER, reverseIntent, intentService,websocket);
            //log.info("the next step is ____________executorService.submit(timer)______________");
            executorService.submit(forwardTimer);
            executorService.submit(reverseTimer);

            SseInventory.releaseScheduledServiceResource(SseInventory.scheduledForwardServices.get(serviceId));
            SseInventory.releaseScheduledServiceResource(SseInventory.scheduledReverseServices.get(serviceId));
            SseInventory.deMarkServiceToResourceMap(SseInventory.scheduledForwardServices.get(serviceId));
            SseInventory.deMarkServiceToResourceMap(SseInventory.scheduledReverseServices.get(serviceId));

            SseInventory.delScheduledService(serviceId);//hlk remove from the ParaMap
            
        }

        catch (Exception e){

        }
        
    }
    public Iterable<LambdaResource> getAvailableLambdas(int startTime, int endTime, Iterable<Link> links) {
        //log.info("6.5----into getAvailableLambdas links {}", links);
        checkNotNull(links);
        Iterator<Link> i = links.iterator();
        checkArgument(i.hasNext());
        Set<LambdaResource> lambdas = new HashSet<LambdaResource>();

        for(int lambdaNo=1;lambdaNo<=SseInventory.lambdaAllNum;lambdaNo++){
            lambdas.add(LambdaResource.valueOf(lambdaNo));
        }
        while (i.hasNext()) {
            Link tmpLink=i.next();
            for(int time=startTime;time<endTime;time++){

                lambdas.retainAll(SseInventory.timeLinkResource.get(time).get(tmpLink));
            }
        }
        //log.info("*************available lambdas {}",lambdas);
        return lambdas;
    }


    private Set<Path> calculateOpticalPath(ConnectPoint src, ConnectPoint dst) {
        Set<Path> paths = pathService.getPaths(src.elementId(), dst.elementId(),new OpticalLinkWeight());
        if (paths.isEmpty()) {

        }        

        return paths;
    }
    private LinkResourceAllocations assignWavelength(int serviceId, int startTime, int endTime, Path path, Set<ResourceRequest> requestLambdas) {
        //log.info("6.5----into assignWavelength");
        Set<ResourceAllocation> allocs = new HashSet<ResourceAllocation>();
        targetLambda.clear();
        Iterator<LambdaResource> lambdaIterator = getAvailableLambdas(startTime,endTime,path.links()).iterator();
        //log.info("assignWavelength, the wavelength on startTime {}, endTime {}, path {}, when serviceId is {}", startTime, endTime, path, serviceId);
        for (ResourceRequest rq:requestLambdas){
            //log.info("6.7 ---- requestLambda {}", rq);
            if (lambdaIterator.hasNext()) {
                LambdaResource tmpLambda=lambdaIterator.next();
                //log.info("6.7----tmpLambda {}", tmpLambda);
                targetLambda.add(tmpLambda);
                allocs.add(new LambdaResourceAllocation(tmpLambda));
            } else {
                //log.info("Failed to allocate lambda resource.");
                return null;
            }
        //break;
        }

        Map<Link, Set<ResourceAllocation>> allocations = new HashMap<Link, Set<ResourceAllocation>>();
        for (Link link : path.links()) {
            allocations.put(link, allocs);
        }

        LinkResourceRequest.Builder request = DefaultLinkResourceRequest.builder(IntentId.valueOf(serviceId),
                                                                                 path.links())
                .addLambdaRequest(requestLambdas);//HLK 10.29
        LinkResourceAllocations result =
                new DefaultLinkResourceAllocations(request.build(), allocations);
        //store.allocateResources(result);
        return result;

    }
    private LinkResourceAllocations buildResourceAllocations(int serviceId, int startTime, int endTime, Path path, Set<ResourceRequest> requestLambdas, Set<LambdaResource> lambdaSet) {
        //log.info("6.5----into assignWavelength");
        Set<ResourceAllocation> allocs = new HashSet<ResourceAllocation>();
        //targetLambda.clear();
        Iterator<LambdaResource> lambdaIterator = lambdaSet.iterator();
        //log.info("assignWavelength, the wavelength on startTime {}, endTime {}, path {}, when serviceId is {}", startTime, endTime, path, serviceId);
        for (ResourceRequest rq:requestLambdas){
            //log.info("6.7 ---- requestLambda {}", rq);
            if (lambdaIterator.hasNext()) {
                LambdaResource tmpLambda=lambdaIterator.next();
                //log.info("6.7----tmpLambda {}", tmpLambda);
                targetLambda.add(tmpLambda);
                allocs.add(new LambdaResourceAllocation(tmpLambda));
            } else {
                //log.info("Failed to allocate lambda resource.");
                return null;
            }
        //break;
        }

        Map<Link, Set<ResourceAllocation>> allocations = new HashMap<Link, Set<ResourceAllocation>>();
        for (Link link : path.links()) {
            allocations.put(link, allocs);
        }

        LinkResourceRequest.Builder request = DefaultLinkResourceRequest.builder(IntentId.valueOf(serviceId),
                                                                                 path.links())
                .addLambdaRequest(requestLambdas);//HLK 10.29
        LinkResourceAllocations result =
                new DefaultLinkResourceAllocations(request.build(), allocations);
        //store.allocateResources(result);
        return result;

    }

    private static class OpticalLinkWeight implements LinkWeight {
        @Override
        public double weight(TopologyEdge edge) {
            if (edge.link().state() == Link.State.INACTIVE) {
                return -1; // ignore inactive links
            }
            if (isOpticalLink(edge.link())) {
                return 1000;  // optical links
            } else {
                return 1;     // packet links
            }
        }
    }



    private static boolean isOpticalLink(Link link) {
        boolean isOptical = false;
        Link.Type lt = link.type();
        if (lt == Link.Type.OPTICAL) {
            isOptical = true;
        }
        return isOptical;
    }


    
    protected ObjectNode packServiceMessage(int serviceId, ConnectPoint src, ConnectPoint dst, Path path) {
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode linkNode = mapper.createObjectNode();
        ArrayNode links = mapper.createArrayNode();

        for(Link link : path.links()) {
            linkNode.put("id",compactLinkString(link))
                    .put("src", link.src().elementId().toString())
                    .put("srcPort", link.src().port().toString())
                    .put("dst", link.dst().elementId().toString())
                    .put("dstPort", link.dst().port().toString());
            links.add(linkNode);
        }
        payload.set("link",links);
        payload.put("result", "success");
        payload.put("serviceId", serviceId);

        return envelope("opticalPathBuildCallback" ,serviceId ,payload);
    }

    /**
     * change the time of scheduled task by intentId
     * 
     * @param intent
     * @param newStartTime
     * @param newEndTime
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public void changeTaskTime(Intent oldIntent, int newStartTime,
            int newEndTime, Intent newIntent) throws InterruptedException,
            ExecutionException {
        if (SseInventory.futureAddList.containsKey(oldIntent.id())) {
            //log.info("go into changeTaskTime! ");
            IntentId inteId = oldIntent.id();
            ScheduledFuture<IntentId> futureAdd = SseInventory.futureAddList
                    .get(inteId);
            ScheduledFuture<IntentId> futureWithdraw = SseInventory.futureWithdrawList
                    .get(inteId);
            futureAdd.cancel(false);
            futureWithdraw.cancel(false);
            SseInventory.futureAddList.remove(inteId);
            SseInventory.futureWithdrawList.remove(inteId);

            InternalCallableTimer timerAdd = new InternalCallableTimer(
                    SseTimerType.ADD_INSTALL_INTENT_TIMER, newIntent,
                    intentService, websocket);
            InternalCallableTimer timerWithdraw = new InternalCallableTimer(
                    SseTimerType.ADD_WITHDRAW_INTENT_TIMER, newIntent,
                    intentService, websocket);
            ScheduledFuture<IntentId> newFutureAdd = executorService.schedule(
                    timerAdd, 10*(newStartTime - SseInventory.currentTime),
                    TimeUnit.SECONDS);
            ScheduledFuture<IntentId> newFutureWithdraw = executorService
                    .schedule(timerWithdraw, 10*(newEndTime
                            - SseInventory.currentTime), TimeUnit.SECONDS);
            SseInventory.futureAddList.put(newIntent.id(), newFutureAdd);
            SseInventory.futureWithdrawList.put(newIntent.id(), newFutureWithdraw);
            log.info("the old intent id is {}; the new intent id is {}", inteId, newIntent.id());
        } else {
            log.error("No muched entry found at changeTaskTime!");
        }
    }



    /**
     * added by yby 
     * 
     */
    class InternalCallableTimer implements Callable<IntentId>{
        private SseTimerType type = null;
        private Intent intent = null;
        private IntentService intentService = null;
        private SseTopologyViewWebSocket timeWebsocket = null;

        public InternalCallableTimer(SseTimerType type, Intent intent, IntentService intentService, SseTopologyViewWebSocket websocket){
            this.type = type;
            this.intent = intent;
            this.intentService = intentService;
            this.timeWebsocket = websocket;
        }

    public IntentId call() throws Exception {
        if(type.equals(SseTimerType.ADD_INSTALL_INTENT_TIMER)) {
            //hlk added
            //log.info("5.22----TimerCall() intent installed!");
            OpticalTimeConnectivityIntent receivedIntent = (OpticalTimeConnectivityIntent)intent;
            ConnectPoint src = receivedIntent.getSrc();
            ConnectPoint dst = receivedIntent.getDst();
            Path path = receivedIntent.getPath();
            //
            timeWebsocket.sendPathResultMessage(packServiceMessage(receivedIntent.getServiceId(), src, dst, path));//send message to GUI
            //log.info("5.22----Timer intent SUBMIT!");
            //intentService.submit(intent);  HLK modified 11.21
            //log.info("5.22----Timer move scheduled to current");
            SseInventory.moveService(receivedIntent.getServiceId());
            timeWebsocket.sendStateMessage("Timer Intent Installed, Service is Going On!", receivedIntent.getServiceId());//hlk 5.26
            return intent.id();
        }
        else if(type.equals(SseTimerType.ADD_WITHDRAW_INTENT_TIMER)) {
            //hlk added
            //log.info("5.22----TimerCall() intent withdraw!");
            OpticalTimeConnectivityIntent receivedIntent = (OpticalTimeConnectivityIntent)intent;
            ConnectPoint src = receivedIntent.getSrc();
            ConnectPoint dst = receivedIntent.getDst();
            Path path = receivedIntent.getPath();
            //log.info("5.28-------withdraw 1");
            SseInventory.delCurrentService(receivedIntent.getServiceId());
            
            timeWebsocket.sendPathResultMessage(packServiceMessage(receivedIntent.getServiceId(), src, dst, path));//send message to GUI
            //intentService.withdraw(intent);  HLK modified 11.21
            //log.info("5.28-------withdraw 2");
            SseInventory.futureAddList.remove(intent.id());
            SseInventory.futureWithdrawList.remove(intent.id());
            timeWebsocket.sendStateMessage("Timer Intent Withdraw, Service is Going Down!", receivedIntent.getServiceId());//hlk 5.26
            return intent.id();
        }else if(type.equals(SseTimerType.CANCEL_INTENT_TIMER)){
            // int i = SseInventory.getFutureListIndex(intent.id());
            //log.info("go into CANCEL_INTENT_TIMER");
            if( SseInventory.futureAddList.containsKey(intent.id())){
                IntentId id = intent.id();
                //log.info("futureAddList.containsKey(intent.id())");
                ScheduledFuture<IntentId> futureAdd = SseInventory.futureAddList.get(id);
                //log.info("the futureAdd is {}", futureAdd.isDone());
                ScheduledFuture<IntentId> futureWithdraw = SseInventory.futureWithdrawList.get(id);
                //log.info("the futureWithdraw is {}", futureWithdraw.isDone());
                // if(!futureAdd.isDone() && !futureWithdraw.isDone()){
                    boolean ad = futureAdd.cancel(false);// interrupt when it 's not running.
                    boolean wd = futureWithdraw.cancel(false);
                    //log.info("the cancel results are {}, {}", ad, wd);
                    SseInventory.futureAddList.remove(id);// remove cancel
                    SseInventory.futureWithdrawList.remove(id);  
                // }
            }else{
                log.error("cann't find matched intent in futureAddList and futureWithdrawList!");
            }
        }else{
            log.error("No matched SseTimerType!");
        }
        return null;      
    }
    
}

/*-----------------yby function schedule-----------------*/ 
 
    /** 
     * complete adjustment about scheduled services avoiding overloading 
     * situation. 
     *  
     * @throws ExecutionException 
     * @throws InterruptedException 
     */ 
    public void reSchedule() throws InterruptedException, 
            ExecutionException { 
        //get the heavy load block
        Set<SseOpticalLambdaLoad> heavyLoadBlock = getLargeLoadResult(); 
        //get the candidate services for reprovisioning
        //TODO
        Map<Integer,CandidateService> candidateServiceMap=getCandidateServices(heavyLoadBlock);

        if(candidateServiceMap.isEmpty()){
            log.info("candidateServiceMap is empty. No need to adjust services!");
        }
        // reschedule one by one
        else{
            for(Map.Entry<Integer,CandidateService> serviceEntry:candidateServiceMap.entrySet()){
                int serviceId= serviceEntry.getKey();
                //log.info("reprovisioning service ID is: {}",serviceId);
                CandidateService candidateService=serviceEntry.getValue();
                Set<Integer> timeSet = candidateService.getTimeSet();
                //log.info("timeSet is: {}", timeSet);
                
                Set<Link> linkSet = candidateService.getLinkSet();
                //log.info("linkSet is: {}", linkSet);
                // pre-calculate if the current condition need to be optimized
                if(checkIfNeedOptimization(timeSet,linkSet)){
                    //log.info("serviceneed to be reprovisioned : {} ", serviceId);
                    int optimizationFlag=reprovisioningService(serviceId,timeSet,linkSet);
                }
            }
        } 
    } 
    
    public Map<Integer,CandidateService> getCandidateServices(Set<SseOpticalLambdaLoad> heavyLoadBlock){

        Map<Integer,CandidateService> returnCandidateServices = new HashMap<Integer,CandidateService>();

        // TO DO get the scheduled service ID range HERE
        for(Map.Entry<Integer, SseOpticalPathParameter> itemService:SseInventory.scheduledForwardServices.entrySet()){
            //candidate service id is i
            int tmpServiceId=itemService.getKey();
            Set<Integer> tmpTimeSet = new HashSet<Integer>();
            Set<Link> tmpLinkSet = new HashSet<Link>();
            for(SseOpticalLambdaLoad heavyLoadBlockItem:heavyLoadBlock){

                Set<Integer> tmpServiceSet=heavyLoadBlockItem.getServiceSet();
                for(Integer serviceIdItem:tmpServiceSet){
                    if(tmpServiceId==serviceIdItem){
                        tmpTimeSet.add(heavyLoadBlockItem.getTime());
                        tmpLinkSet.add(heavyLoadBlockItem.getLink());
                        break;
                    }
                }
            }
            if((tmpTimeSet.size()!=0)||(tmpLinkSet.size()!=0)){
                CandidateService tmpService= new CandidateService(tmpTimeSet,tmpLinkSet);
                returnCandidateServices.put(tmpServiceId,tmpService);
                //log.info("candidateService is : {}", tmpServiceId);
            }
            
        }
        
        return returnCandidateServices;

    }
    Boolean checkIfNeedOptimization(Set<Integer> timeSet,Set<Link> linkSet){

        for(Integer time:timeSet){

            for(Link link:linkSet){
                Set<LambdaResource> candidateBlock=SseInventory.timeLinkResource.get(time).get(link);                
                if(candidateBlock.size()<(SseInventory.lambdaAllNum-SseInventory.lambdaNumThreshold)){
                    return true;
                }
            }
        }
        return false;
    }
    public int reprovisioningService(int serviceId, Set<Integer> timeSet, Set<Link> linkSet){

        switch(SseInventory.optimizationMode){

            case 0:
                return reScheduleOnly(serviceId, timeSet, linkSet);
            case 1:
                return reScheduleReAllocation(serviceId, timeSet, linkSet);
            case 2:
                return reScheduleReRouteReallocation(serviceId, timeSet, linkSet);
            default:
                return -1;
        }
    }

    public int reScheduleOnly(int serviceId, Set<Integer> timeSet, Set<Link> linkSet){

        log.info("service : {} is going into reScheduleOnly Method ",serviceId);
        SseOpticalPathParameter candidateServiceInfo = SseInventory.scheduledForwardServices.get(serviceId);
        int earlyStartTime=candidateServiceInfo.getEarlyStartTime()-SseInventory.currentTime;
        int actualStartTime=candidateServiceInfo.getActualStartTime()-SseInventory.currentTime;
        int actualEndTime =candidateServiceInfo.getActualEndTime()-SseInventory.currentTime;
        int deadLineTime =candidateServiceInfo.getDeadLineTime()-SseInventory.currentTime;
        log.info("earlyStartTime is {}",earlyStartTime);
        log.info("actualStartTime is {}",actualStartTime);
        log.info("actualEndTime is {}",actualEndTime);
        log.info("deadLineTime is {}",deadLineTime);

        int maxTime=0;
        for(int timeItem:timeSet){
            if(timeItem>maxTime){
                maxTime=timeItem;
            }
        }
        log.info("right time is: {}", maxTime);
        int minTime =200;
        for(Integer timeItem:timeSet){
            if(timeItem<minTime){
                minTime=timeItem;
            }
        }
        log.info("left time is: {}", minTime);
        //earlyStartTime-----minTime     maxTime------deadlineTime

        // need to reviese the actual start and end time here
        //get the available lambdas of existing link at each time slots
        int tmpEarlyStartTime= (earlyStartTime>1)?earlyStartTime:1;
        Map<Integer,Set<LambdaResource>> pathLambdas= getPathAvailableLambdas(tmpEarlyStartTime, actualStartTime, actualEndTime, deadLineTime,
            candidateServiceInfo.getPath(),candidateServiceInfo.getAllocatedLambda());
        // get the time slots which contain all the assigned wavelengths.
        Map<Integer,Integer> availableTimeSlots = getAvailableTimeSlots(pathLambdas,candidateServiceInfo.getAllocatedLambda());
        // find tha candidate time periods
        int newStartTime=actualStartTime;
        int maxAvailableLambda =0;
        Map<Integer,Integer> candidateArea= getCandidateArea(availableTimeSlots,actualStartTime,actualEndTime);
        for(Map.Entry<Integer,Integer> itemArea:candidateArea.entrySet()){
            if(itemArea.getValue()>maxAvailableLambda){
                maxAvailableLambda=itemArea.getValue();
                newStartTime = itemArea.getKey();
            }
        }
        log.info("newStartTime is : {}",newStartTime);
        newStartTime=newStartTime+SseInventory.currentTime;
        log.info("newStartTime is : {}",newStartTime);
        int newEndTime=newStartTime+actualEndTime-actualStartTime;
        log.info("newEndTime is : {}",newEndTime);
        refreshServiceParameter(serviceId,candidateServiceInfo,newStartTime,newEndTime,candidateServiceInfo.getAllocatedLambda(),null); 
        return 1;

    }

    public Map<Integer,Set<LambdaResource>> getPathAvailableLambdas(int start, int actualStart,int actualEnd,int end, Path path,Set<LambdaResource> lambdaSet){
        Map<Integer,Set<LambdaResource>> returnPathLambdas = new HashMap<Integer,Set<LambdaResource>>();
        List<Link> candidateLinks = path.links();
        for(int i = start; i<end; i++){
            Set<LambdaResource> returnLambdas= new HashSet<LambdaResource>();
            for(int j =1;j<=SseInventory.lambdaAllNum;j++){
                returnLambdas.add(LambdaResource.valueOf(j));
            }
            for (Link itemLink:candidateLinks){
                returnLambdas.retainAll(SseInventory.timeLinkResource.get(i).get(itemLink));
            }
            returnPathLambdas.put(i,returnLambdas);
        }
        // assume that the current resource is released 
        for(int k=actualStart;k<actualEnd;k++){
            returnPathLambdas.get(k).addAll(lambdaSet);
        }
        //log.info("returnPathLambdas are :{}",returnPathLambdas);
        return returnPathLambdas;
     }
     public Map<Integer,Integer> getAvailableTimeSlots(Map<Integer,Set<LambdaResource>> pathLambdas, Set<LambdaResource> serviceLambdas){
        Map<Integer,Integer> returnTimeMap = new HashMap<Integer,Integer>();
        for(Map.Entry<Integer,Set<LambdaResource>> itemLambdaSet:pathLambdas.entrySet()){
            int candidateTime=itemLambdaSet.getKey();
            Set<LambdaResource> candidateLambdaSet=itemLambdaSet.getValue();
            if(candidateLambdaSet.containsAll(serviceLambdas)){
                returnTimeMap.put(candidateTime,candidateLambdaSet.size());
                //log.info("this time contains the target lambdas {}",candidateTime);
            }
        }
        return returnTimeMap;
     }

     public Map<Integer,Integer> getCandidateArea(Map<Integer,Integer> availableTimeSlots,int actualStartTime,int actualEndTime){

        Map<Integer,Integer> candidateArea = new HashMap<Integer,Integer>();
        for(Map.Entry<Integer,Integer> itemTime:availableTimeSlots.entrySet()){
            Boolean candidateFlag=true;
            int minSlotNum=SseInventory.lambdaAllNum;
            int currentTime=itemTime.getKey();
            for(int i=currentTime;i<currentTime+actualEndTime-actualStartTime;i++){
                if(availableTimeSlots.get(i)!=null){
                    if(availableTimeSlots.get(i)<minSlotNum){
                        minSlotNum=availableTimeSlots.get(i);
                        //TO DO
                    }
                }
                else{
                    candidateFlag=false;
                    break;
                }
            }
            if(candidateFlag){
                candidateArea.put(currentTime,minSlotNum);
            }
        }
        return candidateArea;
     }
    public int reScheduleReAllocation(int serviceId,Set<Integer> timeSet, Set<Link> linkSet){
        log.info("service : {} is going into reScheduleReAllocation Method ",serviceId);
        SseOpticalPathParameter candidateServiceInfo = SseInventory.scheduledForwardServices.get(serviceId);
        int earlyStartTime=candidateServiceInfo.getEarlyStartTime()-SseInventory.currentTime;
        int actualStartTime=candidateServiceInfo.getActualStartTime()-SseInventory.currentTime;
        int actualEndTime =candidateServiceInfo.getActualEndTime()-SseInventory.currentTime;
        int deadLineTime =candidateServiceInfo.getDeadLineTime()-SseInventory.currentTime;
        log.info("earlyStartTime is {}",earlyStartTime);
        log.info("actualStartTime is {}",actualStartTime);
        log.info("actualEndTime is {}",actualEndTime);
        log.info("deadLineTime is {}",deadLineTime);

        int maxTime=0;
        for(int timeItem:timeSet){
            if(timeItem>maxTime){
                maxTime=timeItem;
            }
        }
        log.info("right time is: {}", maxTime);
        int minTime =200;
        for(Integer timeItem:timeSet){
            if(timeItem<minTime){
                minTime=timeItem;
            }
        }
        log.info("left time is: {}", minTime);
        //earlyStartTime-----minTime     maxTime------deadlineTime
        int tmpEarlyStartTime= (earlyStartTime>1)?earlyStartTime:1;
        Map<Integer,Set<LambdaResource>> pathLambdas= getPathAvailableLambdas(tmpEarlyStartTime, actualStartTime, actualEndTime, deadLineTime,
            candidateServiceInfo.getPath(),candidateServiceInfo.getAllocatedLambda());
        log.info("before candidateArea");
        Map<Integer,Set<LambdaResource>> candidateArea= getCandidateAreaWithLambdas(pathLambdas,actualStartTime,actualEndTime);
        
        int newStartTime=actualStartTime;
        int maxAvailableLambda =candidateServiceInfo.getAllocatedLambda().size()-1;
        log.info("before deciding time range");
        Set<LambdaResource> candidateLambdas=new HashSet<LambdaResource>();
        for(Map.Entry<Integer,Set<LambdaResource>> itemArea:candidateArea.entrySet()){
            if(itemArea.getValue().size()>maxAvailableLambda){
                candidateLambdas.clear();
                candidateLambdas.addAll(itemArea.getValue());
                newStartTime = itemArea.getKey();
                maxAvailableLambda=itemArea.getValue().size();
            }
        }

        log.info("before allocation");
        Set<LambdaResource> targetLambdas = new HashSet<LambdaResource>();
        for(int i =1; i<=SseInventory.lambdaAllNum; i++){
            if(candidateLambdas.contains(LambdaResource.valueOf(i))){
                targetLambdas.add(LambdaResource.valueOf(i));
                if(targetLambdas.size()==candidateServiceInfo.getAllocatedLambda().size()){
                    break;
                }
            }
        }
        log.info("old Lambdas {}", candidateServiceInfo.getAllocatedLambda());
        log.info("target Lambdas {}", targetLambdas);
        log.info("newStartTime is : {}",newStartTime);
        newStartTime=newStartTime+SseInventory.currentTime;
        log.info("newStartTime is : {}",newStartTime);
        int newEndTime=newStartTime+actualEndTime-actualStartTime;
        log.info("newEndTime is : {}",newEndTime);
        refreshServiceParameter(serviceId,candidateServiceInfo,newStartTime,newEndTime,targetLambdas,null); 
        return 1;

    }
    public Map<Integer, Set<LambdaResource>> getCandidateAreaWithLambdas(Map<Integer, Set<LambdaResource>> pathLambdas, int actualStartTime, int actualEndTime) {
        Map<Integer, Set<LambdaResource>> candidateArea = new HashMap<Integer, Set<LambdaResource>>();
        int timeLength = actualEndTime - actualStartTime;
        for(Map.Entry<Integer, Set<LambdaResource>> itemTime:pathLambdas.entrySet()){
            Boolean candidateFlag=true;
            Set<LambdaResource> availableArea= new HashSet<LambdaResource>();
            for(int j =1;j<=SseInventory.lambdaAllNum;j++){
                availableArea.add(LambdaResource.valueOf(j));
            }
            int currentTime = itemTime.getKey();
            for(int i = currentTime; i<currentTime+timeLength;i++){
                if(pathLambdas.get(i)!=null){
                    availableArea.retainAll(pathLambdas.get(i));
                }
                else{
                    candidateFlag=false;
                    break;
                }
            }
            if(candidateFlag){
                candidateArea.put(currentTime,availableArea);
                log.info("candidateArea start Time IS {}",currentTime);
                log.info("available Lambda Set is {}",availableArea);
            }
        }
        return candidateArea;
    }
    public int reScheduleReRouteReallocation(int serviceId,Set<Integer> timeSet, Set<Link> linkSet){
        log.info("service : {} is going into reScheduleReAllocation Method ",serviceId);
        SseOpticalPathParameter candidateServiceInfo = SseInventory.scheduledForwardServices.get(serviceId);
        int earlyStartTime=candidateServiceInfo.getEarlyStartTime()-SseInventory.currentTime;
        int actualStartTime=candidateServiceInfo.getActualStartTime()-SseInventory.currentTime;
        int actualEndTime =candidateServiceInfo.getActualEndTime()-SseInventory.currentTime;
        int deadLineTime =candidateServiceInfo.getDeadLineTime()-SseInventory.currentTime;
        log.info("earlyStartTime is {}",earlyStartTime);
        log.info("actualStartTime is {}",actualStartTime);
        log.info("actualEndTime is {}",actualEndTime);
        log.info("deadLineTime is {}",deadLineTime);

        int maxTime=0;
        for(int timeItem:timeSet){
            if(timeItem>maxTime){
                maxTime=timeItem;
            }
        }
        log.info("right time is: {}", maxTime);
        int minTime =200;
        for(Integer timeItem:timeSet){
            if(timeItem<minTime){
                minTime=timeItem;
            }
        }
        log.info("left time is: {}", minTime);
        //earlyStartTime-----minTime     maxTime------deadlineTime
        int tmpEarlyStartTime= (earlyStartTime>1)?earlyStartTime:1;

        int newStartTime=actualStartTime;
        Path newPath = candidateServiceInfo.getPath();
        int maxAvailableLambda =candidateServiceInfo.getAllocatedLambda().size()-1;
        Set<LambdaResource> candidateLambdas=new HashSet<LambdaResource>();

        Set<Path>candidatePaths= calculateOpticalPath(candidateServiceInfo.getSrc(),candidateServiceInfo.getDst());
        
        log.info("before deciding time range");
        Map<Path,Map<Integer,Set<LambdaResource>>> candidatePathAreas= new HashMap<Path,Map<Integer,Set<LambdaResource>>>(); 
        for(Path itemPath:candidatePaths){
            Map<Integer,Set<LambdaResource>> pathLambdas= getNewPathAvailableLambdas(tmpEarlyStartTime, actualStartTime, actualEndTime, deadLineTime,
                candidateServiceInfo.getPath(),itemPath,candidateServiceInfo.getAllocatedLambda());
            Map<Integer,Set<LambdaResource>> candidateAreas= getCandidateAreaWithLambdas(pathLambdas,actualStartTime,actualEndTime);
            
            for(Map.Entry<Integer,Set<LambdaResource>> itemArea:candidateAreas.entrySet()){
                if(itemArea.getValue().size()>maxAvailableLambda){
                    //log.info(start);
                    candidateLambdas.clear();
                    candidateLambdas.addAll(itemArea.getValue());
                    newStartTime = itemArea.getKey();
                    newPath=itemPath;
                    maxAvailableLambda=itemArea.getValue().size();
                }
            }
        }
        log.info("before allocation");
        Set<LambdaResource> targetLambdas = new HashSet<LambdaResource>();
        for(int i =1; i<=SseInventory.lambdaAllNum; i++){
            if(candidateLambdas.contains(LambdaResource.valueOf(i))){
                targetLambdas.add(LambdaResource.valueOf(i));
                if(targetLambdas.size()==candidateServiceInfo.getAllocatedLambda().size()){
                    break;
                }
            }
        }

        log.info("old Lambdas {}", candidateServiceInfo.getAllocatedLambda());
        log.info("target Lambdas {}", targetLambdas);
        if(candidateServiceInfo.getAllocatedLambda().size()!=targetLambdas.size()){
            log.info("allocation error");
        }
        log.info("newStartTime is : {}",newStartTime);
        newStartTime=newStartTime+SseInventory.currentTime;
        log.info("newStartTime is : {}",newStartTime);
        int newEndTime=newStartTime+actualEndTime-actualStartTime;
        log.info("newEndTime is : {}",newEndTime);
        refreshServiceParameter(serviceId,candidateServiceInfo,newStartTime,newEndTime,targetLambdas,newPath); 
        return 1;
    }
    public Map<Integer,Set<LambdaResource>> getNewPathAvailableLambdas(int start, int actualStart,int actualEnd,int end, Path oldPath, Path newPath, Set<LambdaResource> lambdaSet){
        Map<Integer,Set<LambdaResource>> returnPathLambdas = new HashMap<Integer,Set<LambdaResource>>();
        List<Link> oldLinks = oldPath.links();
        List<Link> candidateLinks = newPath.links();
        List<Link> overLappingLinks = new ArrayList<Link>();
        overLappingLinks.addAll(candidateLinks);
        overLappingLinks.retainAll(oldLinks);
        for(int i = start; i<end; i++){
            Set<LambdaResource> returnLambdas= new HashSet<LambdaResource>();
            for(int j =1;j<=SseInventory.lambdaAllNum;j++){
                returnLambdas.add(LambdaResource.valueOf(j));
            }
            for (Link itemLink:candidateLinks){
                Set<LambdaResource> tmpLambdas= new HashSet<LambdaResource>();
                tmpLambdas.addAll(SseInventory.timeLinkResource.get(i).get(itemLink));
                if((i>=actualStart)&&(i<actualEnd)){
                    if(overLappingLinks.contains(itemLink)){
                        tmpLambdas.addAll(lambdaSet);
                    }  
                }
                returnLambdas.retainAll(tmpLambdas);
            }
            returnPathLambdas.put(i,returnLambdas);
        }
        // assume that the current resource is released 
        /*for(int k=actualStart;k<actualEnd;k++){
            returnPathLambdas.get(k).addAll(lambdaSet);
        }*/
        //log.info("returnPathLambdas are :{}",returnPathLambdas);
        return returnPathLambdas;
     }
    private void refreshServiceParameter(int serviceId,SseOpticalPathParameter parameter, int newActualStartTime, int newActualEndTime, Set<LambdaResource> lambdaSet, Path newPath){ 

        Path path;
        Path reversePath;
        if(newPath!=null){
            List<Link> reverseLinkList = new ArrayList<Link>();
            reverseLinkList.addAll(newPath.links());
            Collections.reverse(reverseLinkList);
            List<Link> rebuiltReverseLinkList = new ArrayList<Link>();

            for(Link singleLink : reverseLinkList) {
                Link tempLink = new DefaultLink(singleLink.providerId(), singleLink.dst(), singleLink.src(), singleLink.type(), singleLink.state(), singleLink.isDurable(), singleLink.annotations());
                    //log.info("HLK 6.5----templink {}  singleLink {}", tempLink, singleLink);
                rebuiltReverseLinkList.add(tempLink);
            }
            reversePath= new DefaultPath(newPath.providerId(), rebuiltReverseLinkList, newPath.cost(), newPath.annotations());
            path=newPath;
        }
        else{
            path=parameter.getPath();
            reversePath=parameter.getReversePath();
        }
 
        LinkResourceAllocations forwardResource=getResourceAllocationFromLambda(serviceId,lambdaSet,path); 
        LinkResourceAllocations reverseResource=getResourceAllocationFromLambda(serviceId,lambdaSet,reversePath); 
        if(forwardResource==null){
            log.info("big error, no resources");
        }
        SseOpticalPathParameter forwardParameter =new SseOpticalPathParameter(parameter.getServiceId(), parameter.getEarlyStartTime(), newActualStartTime, newActualEndTime,  
                                parameter.getDeadLineTime(), parameter.getSrc(), parameter.getDst(), path, reversePath, forwardResource, reverseResource,lambdaSet); 
        SseOpticalPathParameter reverseParameter =new SseOpticalPathParameter(parameter.getServiceId(), parameter.getEarlyStartTime(), newActualStartTime, newActualEndTime,  
                                parameter.getDeadLineTime(), parameter.getDst(), parameter.getSrc(), reversePath, path, reverseResource,forwardResource,lambdaSet); 
                                 
        Intent oldForwardIntent = SseInventory.scheduledForwardServiceIntents.get(serviceId); 
        Intent oldReverseIntent = SseInventory.scheduledReverseServiceIntents.get(serviceId); 
 
        OpticalTimeConnectivityIntent newForwardIntent = new OpticalTimeConnectivityIntent( 
            oldForwardIntent.appId(), forwardParameter.getSrc(), forwardParameter.getDst(), 
            forwardParameter.getLinkResourceAllocations(), 
            forwardParameter.getPath(), parameter.getServiceId()); 
                                //TO DO 
        OpticalTimeConnectivityIntent newReverseIntent = new OpticalTimeConnectivityIntent( 
            oldReverseIntent.appId(), forwardParameter.getDst(),forwardParameter.getSrc(),  
            forwardParameter.getReverseLinkResourceAllocations(), 
            forwardParameter.getReversePath(), parameter.getServiceId());/// 
        
        SseInventory.releaseScheduledServiceResource(SseInventory.scheduledForwardServices.get(serviceId));// TODO 
        SseInventory.releaseScheduledServiceResource(SseInventory.scheduledReverseServices.get(serviceId)); 
        
        SseInventory.resourceReservation(forwardParameter.getActualStartTime()-SseInventory.currentTime, 
            forwardParameter.getActualEndTime()-SseInventory.currentTime, forwardParameter.getPath(), 
            forwardParameter.getAllocatedLambda()); 
        SseInventory.resourceReservation(reverseParameter.getActualStartTime()-SseInventory.currentTime, 
            reverseParameter.getActualEndTime()-SseInventory.currentTime, reverseParameter.getPath(), 
            reverseParameter.getAllocatedLambda()); 
 
        SseInventory.deMarkServiceToResourceMap(SseInventory.scheduledForwardServices.get(serviceId)); 
        SseInventory.deMarkServiceToResourceMap(SseInventory.scheduledReverseServices.get(serviceId)); 
        SseInventory.markServiceToResourceMap(serviceId,(newActualStartTime - SseInventory.currentTime) , (newActualEndTime - SseInventory.currentTime), path,lambdaSet); 
        SseInventory.markServiceToResourceMap(serviceId,(newActualStartTime - SseInventory.currentTime) , (newActualEndTime - SseInventory.currentTime), reversePath,lambdaSet); 
                                // TO DO 
        try{ 
            changeTaskTime(oldForwardIntent, forwardParameter.getActualStartTime(), 
                    forwardParameter.getActualEndTime(), newForwardIntent); 
            changeTaskTime(oldReverseIntent, reverseParameter.getActualStartTime(), 
                    reverseParameter.getActualEndTime(), newReverseIntent); 
        } 
        catch(Exception e){ 
            // TO DO 
        } 
 
        SseInventory.scheduledForwardServiceIntents.replace(serviceId, newForwardIntent); 
        SseInventory.scheduledReverseServiceIntents.replace(serviceId, newReverseIntent); 
        SseInventory.scheduledForwardServices.replace(serviceId, forwardParameter); 
        SseInventory.scheduledReverseServices.replace(serviceId, reverseParameter); 
 
        // send message to inform this change 
     
    } 
    /** 
     * generate all available lambdas Set 
     *  
     * @return 
     */ 
    private Set<LambdaResource> getAllLambdas() { 
        Set<LambdaResource> lambdas = new HashSet<LambdaResource>(); 
        for (int i = 0; i < SseInventory.lambdaAllNum; i++) { 
            lambdas.add(LambdaResource.valueOf(i + 1)); 
        } 
        return lambdas; 
    } 
 
        /**  
     * get the load situation per optical link per time unit which is beyond  
     * SseInventory lambdaNumThreshold.  
     *   
     * @return  
     */  
    private Set<SseOpticalLambdaLoad> getLargeLoadResult() {  
        Set<SseOpticalLambdaLoad> returnOverLoadsSet = new HashSet<SseOpticalLambdaLoad>();  
        //Map<Link,Map<Long,Set<Long>>> overLoadMap = new HashMap<Link,Map<Long,Set<Long>>>(); // link time serviceSet 
 
        scheduledResource = SseInventory.timeLinkServiceResource;  
  
        if (scheduledResource.isEmpty()) {  
            log.error("scheduledResource.isEmpty");  
        } 
        else {  
            for (Entry<Integer, Map<Link, Map<Integer, Set<LambdaResource>>>> entryLong : scheduledResource.entrySet()) {  
                int time = entryLong.getKey();  
                Map<Link, Map<Integer, Set<LambdaResource>>> tmp = entryLong.getValue();  

                if (tmp.isEmpty()) {  
                    log.error("error in getLargeLoadResult()---> if(tmp.isEmpty())");  
                }  
                else {  
                    for (Entry<Link, Map<Integer, Set<LambdaResource>>> entryLink : tmp.entrySet()) {  

                        Link link = entryLink.getKey();  
                        int lambdaNum = 0; 
                        Map<Integer, Set<LambdaResource>> lambdaMap = entryLink.getValue();  
                        if(lambdaMap==null){ 
                            log.info("lambdaMap is empty"); 
                            continue;
                        }   
                        Set<Integer> serviceSet= new HashSet<Integer>();  
                        for (Entry<Integer, Set<LambdaResource>> entryLambda : lambdaMap.entrySet()) {  
                            //occupied lambdaNum
                            lambdaNum = lambdaNum + entryLambda.getValue().size();  
                            serviceSet.add(entryLambda.getKey());  
                        } 
                        if (lambdaNum >= SseInventory.lambdaNumThreshold) {
                            log.info("heavyLoadTime is : {}", time);
                            log.info("heavyLoadLink is : {}", link);  
                            log.info("heavyLoadOccupiedNum is {}:", lambdaNum);
                            SseOpticalLambdaLoad tmpOverLoadBlock= new SseOpticalLambdaLoad(time,link,lambdaNum,serviceSet);
                            returnOverLoadsSet.add(tmpOverLoadBlock);// each incluing time link and service info 
 
                        }  
                    }  
                }  
            }  
        }  
        return returnOverLoadsSet;  
    }

 
    public LinkResourceAllocations getResourceAllocationFromLambda(int serviceId, Set<LambdaResource> lambdas, Path path){ 
         
        Set<ResourceRequest> requestResource = new HashSet<ResourceRequest>(); 
        for(int i=0; i<lambdas.size(); i++) { 
            requestResource.add(new LambdaResourceRequest()); 
        } 
        Set<ResourceAllocation> allocs = new HashSet<ResourceAllocation>(); 

        for (LambdaResource tmpLambda:lambdas){ 
            allocs.add(new LambdaResourceAllocation(tmpLambda)); 
        //break; 
        } 
 
        Map<Link, Set<ResourceAllocation>> allocations = new HashMap<Link, Set<ResourceAllocation>>(); 
        for (Link link : path.links()) { 
            allocations.put(link, allocs); 
        } 
 
        LinkResourceRequest.Builder request = DefaultLinkResourceRequest.builder(IntentId.valueOf(serviceId), 
                                                                                 path.links()) 
                .addLambdaRequest(requestResource);//HLK 10.29
        LinkResourceAllocations result = 
                new DefaultLinkResourceAllocations(request.build(), allocations); 
        return result; 
    }

}