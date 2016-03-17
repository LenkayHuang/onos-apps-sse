package org.onosproject.sse;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.onosproject.net.resource.link.LinkResourceService;
import org.onosproject.net.resource.link.LinkResourceAllocations;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.resource.link.LambdaResource;
import org.onosproject.net.resource.ResourceAllocation;
import org.onosproject.net.resource.link.LambdaResourceAllocation;
import org.onosproject.net.intent.OpticalTimeConnectivityIntent;
import org.onosproject.net.intent.IntentId;

import org.onosproject.sse.SseOpticalPathParameter;
import org.onlab.osgi.ServiceDirectory;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ExecutionException;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SseInventory {

	protected static final Logger log = LoggerFactory.getLogger(SseInventory.class);
	public static int TIMESLOTS=200;//hlk modified 6.3
	private static LinkResourceService linkResourceService;
	private static SseTopologyViewWebSocket websocket;
	private static LinkService linkService;
	public static int initDate = 20150520;
	public static int initTime = 0;
	public static int currentTime = 0;
	public static int startFlag=0;

	public static int optimizationMode=1;
	public static final int lambdaNumThreshold = 30;

	public static Boolean logResultFlag=true;

	public static int failedServiceNum=0;

	public static int lastService=0;

	//public static long statisticsTimeSlost=0;
	public static Boolean statisticsStartFlag=false;
	public static int statisticsStartTime=10000;
	public static int statisticsEndTime=10000;
	//public static Boolean statisticsEndFlag=false;
	public static double totalAvailableTimeSlots=0;

	public static final int lambdaAllNum = 40;//yby

	public static String linkMark = "";
	public static ObjectMapper mapper = new ObjectMapper();

	public static Map<Integer, Integer> statisticTimeSlot=new HashMap<Integer,Integer>();
	public static Set<Link> topologyLinks = new HashSet<Link>();
	public static Map<Integer, Map<Link,Set<LambdaResource>>> timeLinkResource = new HashMap<Integer, Map<Link,Set<LambdaResource>>>();// time Link availableLambda
	public static Map<Integer, Map<Link,Map<Integer,Set<LambdaResource>>>> timeLinkServiceResource = new HashMap<Integer, Map<Link,Map<Integer,Set<LambdaResource>>>>();// time Link Service Lambda
	public static Map<Link, Map<Integer, Integer>> reformedTimeLinkResource = new HashMap<Link, Map<Integer, Integer>>();//for overall preview
	public static Map<Link, Map<Integer, Map<Integer,Set<LambdaResource>>>> reformedTimeLinkServiceResource = new HashMap<Link, Map<Integer, Map<Integer,Set<LambdaResource>>>>();//link service time set

	public static Map<IntentId, ScheduledFuture<IntentId>> futureAddList = new HashMap<IntentId, ScheduledFuture<IntentId>>();
	public static Map<IntentId, ScheduledFuture<IntentId>> futureWithdrawList = new HashMap<IntentId, ScheduledFuture<IntentId>>();

	public static Map<Integer, SseOpticalPathParameter> scheduledForwardServices = new HashMap<Integer, SseOpticalPathParameter>();
	public static Map<Integer, SseOpticalPathParameter> scheduledReverseServices = new HashMap<Integer, SseOpticalPathParameter>();
	
	public static Map<Integer, SseOpticalPathParameter> currentForwardServices = new HashMap<Integer, SseOpticalPathParameter>();
	public static Map<Integer, SseOpticalPathParameter> currentReverseServices = new HashMap<Integer, SseOpticalPathParameter>();

	public static Map<Integer, OpticalTimeConnectivityIntent> scheduledForwardServiceIntents = new HashMap<Integer, OpticalTimeConnectivityIntent>(); 
	public static Map<Integer, OpticalTimeConnectivityIntent> scheduledReverseServiceIntents = new HashMap<Integer, OpticalTimeConnectivityIntent>(); 
	public static Map<Integer, OpticalTimeConnectivityIntent> currentForwardServiceIntents = new HashMap<Integer, OpticalTimeConnectivityIntent>(); 
	public static Map<Integer, OpticalTimeConnectivityIntent> currentReverseServiceIntents = new HashMap<Integer, OpticalTimeConnectivityIntent>(); 
	//public static Map<Long, Map<Link,Map<Long,Set<Lambda>>>> timeLinkServiceResource = new HashMap<Long, Map<Long,Map<Link,Set<Lambda>>>>();

	//public static final int lambdaNumThreshold = 40;//yby: max lambdas number per time unit per link

	/*static data initialization--------------------------------------------*/
	public static void inventoryInit(ServiceDirectory directory) {
		linkService = directory.get(LinkService.class);
		linkResourceService = directory.get(LinkResourceService.class);	
	}

	public static void setWebsocket(SseTopologyViewWebSocket socket) {
		websocket = socket;
	}

	public static void topologyLinksInit() {
		//log.info("HLK print what is in the Set<Link> {}", topologyLinks);
		if (topologyLinks.isEmpty() == false) {
			topologyLinks.clear();//clear the all the links and obtain them for another time.
		}
		
		// get all the links from core.
		for (Link link : linkService.getLinks()) {
			topologyLinks.add(link);
		}
	}

	public static void timeLinkResourceInit() {
		for(int t=0;t<TIMESLOTS;t++) {
			Map<Link,Set<LambdaResource>> tmpLinkResource = new HashMap<Link,Set<LambdaResource>>();
			for(Link linkItr:topologyLinks){
				Set<LambdaResource> tmpSetLambda= new HashSet<LambdaResource>();
					//HLK modified for SSE 1.3
					for(int i = 1;i<=lambdaAllNum;i++) {
						tmpSetLambda.add(LambdaResource.valueOf(i));
					}
				//tmpSetLambda.addAll(linkResourceService.getAvailableLambdas(linkItr));
				tmpLinkResource.put(linkItr,tmpSetLambda);
			}
			//tmpTimeLinkResource.putAll(linkResource);
			timeLinkResource.put(t,tmpLinkResource);//hlk create a time-linkresource map
		}
	}

	public static void timeLinkServiceResourceInit() {
		for(int t=0;t<TIMESLOTS;t++) {
			Map<Link,Map<Integer,Set<LambdaResource>>> tmpLinkServiceResource = new HashMap<Link,Map<Integer,Set<LambdaResource>>>();
			for(Link linkItr:topologyLinks){
				Map<Integer,Set<LambdaResource>> tmpServiceSetLambda = new HashMap<Integer,Set<LambdaResource>>();
				//tmpSetLambda.addAll(linkResourceService.getAvailableLambdas(linkItr));
				tmpLinkServiceResource.put(linkItr,tmpServiceSetLambda);
			}
			//tmpTimeLinkResource.putAll(linkResource);
			timeLinkServiceResource.put(t,tmpLinkServiceResource);//hlk create a time-linkresource map
		}
	}

	// should be involked by timer class
	public static void refreshTimeResource() {

		if((currentTime>statisticsStartTime)){
			for(Map.Entry<Link,Set<LambdaResource>> itemLink:timeLinkResource.get(0).entrySet())
			totalAvailableTimeSlots+=itemLink.getValue().size();
		}
		if((currentTime>statisticsEndTime)&&logResultFlag){
			double unUsedRatio=totalAvailableTimeSlots/(18*lambdaAllNum*(statisticsEndTime-statisticsStartTime));
			log.info("statisticsStartTime is {}",statisticsStartTime);
			log.info("statisticsEndTime is {}",statisticsEndTime);
			log.info("unUsedRatio is {}",unUsedRatio);
			logResultFlag=false;
		}

		for(int t=0;t<TIMESLOTS-1;t++){
			timeLinkResource.put(t,timeLinkResource.get(t+1));
			timeLinkServiceResource.put(t,timeLinkServiceResource.get(t+1));
		}
		Map<Link,Set<LambdaResource>> tmpLinkResource = new HashMap<Link,Set<LambdaResource>>();
		for(Link linkItr:topologyLinks){
			Set<LambdaResource> tmpSetLambda= new HashSet<LambdaResource>();
			for(int i=1;i<=lambdaAllNum;i++){
				tmpSetLambda.add(LambdaResource.valueOf(i));
			}
			tmpLinkResource.put(linkItr,tmpSetLambda);
		}
		timeLinkResource.put(TIMESLOTS-1,tmpLinkResource);

		Map<Link,Map<Integer,Set<LambdaResource>>> tmpLinkServiceResource = new HashMap<Link,Map<Integer,Set<LambdaResource>>>();
		for(Link linkItr:topologyLinks){
			Map<Integer,Set<LambdaResource>> tmpServiceSetLambda = new HashMap<Integer,Set<LambdaResource>>();
			//tmpSetLambda.addAll(linkResourceService.getAvailableLambdas(linkItr));
			tmpLinkServiceResource.put(linkItr,tmpServiceSetLambda);
		}
		timeLinkServiceResource.put(TIMESLOTS-1,tmpLinkServiceResource);
    }

//*********************************************************************************************************
	public static void addScheduledService(int serviceId, OpticalTimeConnectivityIntent forwardServiceIntent, 
		OpticalTimeConnectivityIntent reverseServiceIntent, SseOpticalPathParameter forwardService, SseOpticalPathParameter reverseService){

		if(scheduledForwardServices.get(serviceId)!=null){

			//log.info("same id error, the service has been scheduled, and the serviceId is {}",serviceId);
		}
		else{
			scheduledForwardServices.put(serviceId,forwardService);
			scheduledReverseServices.put(serviceId,reverseService);
			scheduledForwardServiceIntents.put(serviceId,forwardServiceIntent);
			scheduledReverseServiceIntents.put(serviceId,reverseServiceIntent);
		}
	}

	public static void markServiceToResourceMap(int serviceId,int startTime, int endTime, Path path, Set<LambdaResource> lambdaAllocation){
		//log.info("serviceId is {}",serviceId);
		//log.info("lambdaSet is {}",lambdaAllocation);
		//log.info("path info when marking resource is {}",path);
		for(int i=startTime;i<endTime;i++){
			for(Link linkItem:path.links()){
				timeLinkServiceResource.get(i).get(linkItem).put(serviceId,lambdaAllocation);
				//log.info("6-10  has it been put? {}",timeLinkServiceResource.get(i).get(linkItem));
			}
		}
	}

	public static void deMarkServiceToResourceMap(SseOpticalPathParameter serviceItem){

		int startTime=serviceItem.getActualStartTime()-currentTime;
		int endTime=serviceItem.getActualEndTime()-currentTime;
		int serviceId=serviceItem.getServiceId();
		Path path=serviceItem.getPath();
		Set<LambdaResource> lambdaSet=serviceItem.getAllocatedLambda();
		if(startTime<0){
			startTime=0;
		}
		for(int i=startTime;i<endTime;i++){
			for(Link linkItem:path.links()){
				timeLinkServiceResource.get(i).get(linkItem).remove(serviceId);
			}
		}
	}

	public static void moveService(int serviceId) {
		//log.info("5.22********************************Optical intent serviceId == {}", serviceId);

		if(scheduledForwardServices.get(serviceId)!=null){
			currentForwardServices.put(serviceId, scheduledForwardServices.get(serviceId));
			scheduledForwardServices.remove(serviceId);
			currentForwardServiceIntents.put(serviceId, scheduledForwardServiceIntents.get(serviceId));
			scheduledForwardServiceIntents.remove(serviceId);
		}
		else if (scheduledReverseServices.get(serviceId)!=null) {
			currentReverseServices.put(serviceId, scheduledReverseServices.get(serviceId));
			scheduledReverseServices.remove(serviceId);
			currentReverseServiceIntents.put(serviceId, scheduledReverseServiceIntents.get(serviceId));
			scheduledReverseServiceIntents.remove(serviceId);
			
		}
		else{

			//log.info("error when moving service method");
		}
	}

	public static void delCurrentService(int serviceId) {

		if(currentForwardServices.get(serviceId)!=null){
			currentForwardServices.remove(serviceId);
			currentForwardServiceIntents.remove(serviceId);
			//releaseScheduledServiceResource(scheduledForwardServices.get(serviceId));
		}
		else if(currentReverseServices.get(serviceId)!=null){
			currentReverseServices.remove(serviceId);
			currentReverseServiceIntents.remove(serviceId);
			//releaseScheduledServiceResource(scheduledReverseServices.get(serviceId));
		}
		else{
			//log.info("error in deleting service method");
		}



	}

	public static void delScheduledService(int serviceId) {
		if(scheduledForwardServices.get(serviceId)!=null){
			scheduledForwardServices.remove(serviceId);
			scheduledForwardServiceIntents.remove(serviceId);

			//releaseScheduledServiceResource(scheduledForwardServices.get(serviceId));
		}
		else if(scheduledForwardServices.get(serviceId)!=null){
			scheduledReverseServices.remove(serviceId);
			scheduledReverseServiceIntents.remove(serviceId);

			//releaseScheduledServiceResource(scheduledReverseServices.get(serviceId));
		}
		else{

			//log.info("error in deleting method");
		}
	}


//*********************************************************************************************************
    public static void reformingTimeLinkResource() {
    	//hlk this method PUT ALL PARAMETERS from Map<Time, Map<Link,setSize>> to Map<Link, Map<Time, setSize>>
    	log.info("5.26------------------------reforming");
    	reformedTimeLinkResource.clear();
		for(Map.Entry<Integer, Map<Link,Set<LambdaResource>>> entry1 : timeLinkResource.entrySet()) {
			//log.info("5.26 reforming time {}", entry1.getKey());
			//time = entry1.getKey(), map<link, set<lambda>> = entry1.getValue()
			for(Map.Entry<Link,Set<LambdaResource>> entry2 : entry1.getValue().entrySet()) {
				//link = entry2.getKey(), set<lambda> = entry2.getValue()
				//Map<Long,Integer> tempTimeLambdasMap = new HashMap<Long, Integer>();

				int tempTime= entry1.getKey(); // TIME 
				Link tempLink= entry2.getKey(); // LINK
				int tempSize= entry2.getValue().size(); // AVAILABLE SIZE

				if(reformedTimeLinkResource.get(tempLink) == null) {
					reformedTimeLinkResource.put(tempLink, new HashMap<Integer, Integer>());
					reformedTimeLinkResource.get(tempLink).put(tempTime, tempSize);
				}
				else {
					reformedTimeLinkResource.get(tempLink).put(tempTime, tempSize);
				}
			}	
		}
    }

    public static void reformingTimeLinkServiceResource () {
    	reformedTimeLinkServiceResource.clear();
    	for(Link linkItem:topologyLinks){
    		reformedTimeLinkServiceResource.put(linkItem, new HashMap<Integer,Map<Integer,Set<LambdaResource>>>());
    	}
    	for(Map.Entry<Integer, Map<Link, Map<Integer,Set<LambdaResource>>>> entry1 : timeLinkServiceResource.entrySet()) {
    		int tmpTime= entry1.getKey();
    		if(tmpTime>40){
    			break;
    		}
    		for(Map.Entry<Link, Map<Integer,Set<LambdaResource>>> entry2 : entry1.getValue().entrySet()) {
    			Link tmpLink = entry2.getKey();
    			Map<Integer,Set<LambdaResource>> serviceLambdas = new HashMap<Integer,Set<LambdaResource>>();
    			//for(Map.Entry<Long,Set<Lambda>> entry3 : entry2.getValue().entrySet()) {
    			serviceLambdas.putAll(entry2.getValue());
    			//log.info("6.10------ {}",serviceLambdas);
    			if(reformedTimeLinkServiceResource.get(tmpLink).get(tmpTime)==null){
    				reformedTimeLinkServiceResource.get(tmpLink).put(tmpTime,new HashMap<Integer,Set<LambdaResource>>());
    			}
    			reformedTimeLinkServiceResource.get(tmpLink).get(tmpTime).putAll(serviceLambdas);
    		}
    	}
    }

	/*--------------resource management-------------*/


    public static Boolean isResourceAvailable(int startTime, int endTime, Path path, LinkResourceAllocations allocation) {
        List<Link> candidateLinks= new ArrayList<>();
        candidateLinks=path.links();
        for(Link linkItr: candidateLinks){
        	Set<ResourceAllocation> candidateAllocations = new HashSet<ResourceAllocation>();
        	candidateAllocations=allocation.getResourceAllocation(linkItr);
        		for(ResourceAllocation lambdaAllocation:candidateAllocations){
        			if(lambdaAllocation instanceof LambdaResourceAllocation){
        				LambdaResourceAllocation lambdaresourceAllocation =(LambdaResourceAllocation)lambdaAllocation;
        				LambdaResource candidateLambda=lambdaresourceAllocation.lambda();
        				for(int time = startTime; time< endTime;time++) {
        					if(timeLinkResource.get(time).get(linkItr).contains(candidateLambda)) {
        					continue;
        					}
        					else{
        						return false;
        					}
        				}
        			}
        			else
        			{
        				//log.info("allocations are not lambdaResourceAllocations");
        				return false;
        			}
        		}
        }
        return true;

    }

    public static Boolean resourceReservation(int startTime, int endTime, Path path, Set<LambdaResource> lambdas){
        
        
        //public static Map<Long, Map<Link,Set<Lambda>>> timeLinkResource = new HashMap<Long, Map<Link,Set<Lambda>>>();// time Link Lambda
        for(int time = startTime; time< endTime;time++){ //TO CHECK
        
        	for(Link linkItr: path.links()){
        		//log.info("%%%%%%%Path is {}", linkItr);
        		//Set<ResourceAllocation> candidateAllocations=allocation.getResourceAllocation(linkItr);
        		for(LambdaResource lambda:lambdas){
        			//log.info("%%%%%%%%%%%%% lambda is {}",candidateLambda);
        			//lambdaResource.remove(candidateLambda);
        			if(timeLinkResource.get(time).get(linkItr).contains(lambda)){
        				timeLinkResource.get(time).get(linkItr).remove(lambda);
        				//log.info("lambdas after remove is {}", timeLinkResource.get(time).get(linkItr));
        			}
        			else{
        				//log.error("6.10*************resource is not computed correctly");
        				return false;
        			}
        		}	

        	}
        		
        }
        //log.info("the resource of startTime after reservation is {}",timeLinkResource.get(startTime));
        return true;	
    }
        





	public static Boolean releaseScheduledServiceResource(SseOpticalPathParameter serviceItem) {
        //log.info("5.23 ----2  1");
		int startTime = serviceItem.getActualStartTime() - currentTime;//use to store start & end time
		int endTime = serviceItem.getActualEndTime() - currentTime;
		Path path = serviceItem.getPath();

		LinkResourceAllocations allocation = serviceItem.getLinkResourceAllocations();
		//release the lambda resource on forward and reverse links
		if(startTime<0){
			startTime=0; // to release the remaining resource for current sevices
		}
		if(endTime<=0){
			return true;
		}
        for(int time = startTime; time< endTime; time++) {
        	for(Link linkItr: path.links()){
        		Set<ResourceAllocation> candidateAllocations=allocation.getResourceAllocation(linkItr);
        		for(ResourceAllocation lambdaAllocation:candidateAllocations){
        			if(lambdaAllocation instanceof LambdaResourceAllocation){
        				LambdaResource candidateLambda = ((LambdaResourceAllocation)lambdaAllocation).lambda();
        				if(timeLinkResource.get(time).get(linkItr).contains(candidateLambda)){
        					//log.error("6.10*************resource is not marked correctly");
        					return false;
        				}
        				else{
        					timeLinkResource.get(time).get(linkItr).add(candidateLambda);
        				}
        			}	
        			else{
        				return false;
        			}
        		}
        		//tmpTopoResource.put(linkItr,tmpLambdaList);
        	}
        	//timeLinkResource.put(time,tmpTopoResource);
        }
        return true;
	}

	public static void timerShowPreview () {
		reformingTimeLinkResource();
        //Map<Link, Map<Long, Integer>> map = reformedTimeLinkResource;

        for(Map.Entry<Link, Map<Integer, Integer>> linkEntry : reformedTimeLinkResource.entrySet()) {
            websocket.sendMessage(websocket.showPreviewMessage("showPreview", 404L, linkEntry.getKey(), linkEntry.getValue()));
            log.info("sending a preview message {} {}",linkEntry.getKey() ,linkEntry.getValue());
        }
        /*reformingTimeLinkServiceResource();
        if(linkMark != "") {
            for(Map.Entry<Link, Map<Long,Map<Long,Set<Lambda>>>> entry1 : reformedTimeLinkServiceResource.entrySet()) {
                //log.info("6.8----into");
                if(websocket.compactLinkString(entry1.getKey()).equals(linkMark)) {

                    for(Map.Entry<Long,Map<Long,Set<Lambda>>> entry2 : entry1.getValue().entrySet()) {
                        Long timeItem= entry2.getKey();

                        if(timeItem>40){
                            break;
                        }
                        ObjectNode returnPayload = mapper.createObjectNode();
                        ArrayNode services = mapper.createArrayNode();
                        
                        returnPayload.put("link",linkMark);
                        returnPayload.put("time",String.valueOf(timeItem));
                        for(Map.Entry<Long,Set<Lambda>> entry3 : entry2.getValue().entrySet()){
                            ObjectNode service = mapper.createObjectNode();

                            ArrayNode lambdas = mapper.createArrayNode();
                            service.put("id",String.valueOf(entry3.getKey()));
                            
                            for(Lambda lambdaItem:entry3.getValue()){
                                ObjectNode lambda = mapper.createObjectNode();
                                lambda.put("lambda",lambdaItem.toString());
                                lambdas.add(lambda);
                            }
                            service.put("lambdas",lambdas);
                            services.add(service);
                        }
                        returnPayload.put("services",services);
                        ObjectNode sendBack = websocket.envelope("showLinkDetailInit",404L,returnPayload);
                        //log.info("6.7--HLK--payload2 {}",returnPayload);
                        websocket.sendMessage(sendBack);              
                    }
                }
                else {
                    continue;
                }
            }
        }*/
	}

	
}