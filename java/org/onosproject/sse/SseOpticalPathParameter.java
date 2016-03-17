package org.onosproject.sse;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.Path;
import org.onosproject.net.HostId;
import org.onosproject.net.resource.link.LambdaResource;
import org.onosproject.net.resource.link.DefaultLinkResourceAllocations;
import org.onosproject.net.resource.ResourceAllocation;
import org.onosproject.net.resource.link.LambdaResourceAllocation;
import org.onosproject.net.resource.ResourceRequest;
import org.onosproject.net.resource.link.LambdaResourceRequest;
import org.onosproject.net.resource.link.LinkResourceRequest;
import org.onosproject.net.resource.link.DefaultLinkResourceRequest;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.intent.IntentId;

import org.onosproject.net.resource.link.LinkResourceAllocations;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;


public class SseOpticalPathParameter {

    private int earlyStartTime;

    private int actualStartTime;

    private int actualEndTime;

    private int deadLineTime;

    private int serviceId;

    private Path path;

    private Path reversePath;

    private ConnectPoint src;

    private ConnectPoint dst;

    private LinkResourceAllocations linkResourceAllocations;//wavelength

    private LinkResourceAllocations reverseLinkResourceAllocations;//wavelength

    private Set<LambdaResource> allocatedLambdas;

    public SseOpticalPathParameter(int serviceId, int earlyStartTime,int actualStartTime, int actualEndTime, int deadLineTime, 
        ConnectPoint src, ConnectPoint dst, Path path, Path reversePath, LinkResourceAllocations linkResourceAllocations,LinkResourceAllocations reverseLinkResourceAllocations, Set<LambdaResource> allocatedLambdas) 
        {
    	//this.appId = appId;
        this.earlyStartTime = earlyStartTime;
        this.actualStartTime = actualStartTime;
        this.actualEndTime = actualEndTime;
        this.deadLineTime = deadLineTime;
    	this.serviceId = serviceId;
    	this.src = src;
    	this.dst = dst;
        this.path = path;
        this.reversePath=reversePath;
    	this.linkResourceAllocations = linkResourceAllocations;
        this.reverseLinkResourceAllocations= reverseLinkResourceAllocations;
        this.allocatedLambdas=allocatedLambdas;
    }

    public int getEarlyStartTime() {
        return earlyStartTime;
    }
    
    public void setEarlyStartTime(int earlyStartTime) {
        this.earlyStartTime = earlyStartTime;
    }


    public int getActualStartTime() {
        return actualStartTime;
    }
    
    public void setActualStartTime(int actualStartTime) {
        this.actualStartTime = actualStartTime;
    }


    public int getActualEndTime() {
        return actualEndTime;
    }
    
    public void setActualEndTime(int actualEndTime) {
        this.actualEndTime = actualEndTime;
    }


    public int getDeadLineTime() {
        return deadLineTime;
    }
    
    public void setDeadLineTime(int deadLineTime) {
        this.deadLineTime = deadLineTime;
    }


    public int getServiceId() {
        return serviceId;
    }
    
    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }


    public Path getPath() {
        return path;
    }
    
    public void setPath(Path path) {
        this.path = path;
    }
    public Path getReversePath(){
        return reversePath; 
    }
    public void setReversePath(Path reversePath){
        this.reversePath=reversePath; 
    }

    public ConnectPoint getSrc() {
        return src;
    }
    
    public void setSrc(ConnectPoint src) {
        this.src = src;
    }


    public ConnectPoint getDst() {
        return dst;
    }
    
    public void setDst(ConnectPoint dst) {
        this.dst = dst;
    }


    public LinkResourceAllocations getLinkResourceAllocations() {
        return linkResourceAllocations;
    }

    public LinkResourceAllocations getReverseLinkResourceAllocations() {
        return reverseLinkResourceAllocations;
    }
    
    public void setLinkResourceAllocations(LinkResourceAllocations linkResourceAllocations) {
        this.linkResourceAllocations = linkResourceAllocations;
    }

    public void setReverseLinkResourceAllocations(LinkResourceAllocations reverseLinkResourceAllocations) {
        this.reverseLinkResourceAllocations = reverseLinkResourceAllocations;
    }

    public Set<LambdaResource> getAllocatedLambda(){
        return allocatedLambdas;
    }

    public void setAllocatedLambda(Set<LambdaResource> allocatedLambdas){
        this.allocatedLambdas=allocatedLambdas;
    }

    public void setAllocatedLambdas(Set<LambdaResource> allocatedLambdas) {
        this.allocatedLambdas = allocatedLambdas;
        Set<ResourceRequest> requestResource = new HashSet<ResourceRequest>();
        for(int i=0; i<allocatedLambdas.size(); i++) {
            requestResource.add(new LambdaResourceRequest());
        }
        Set<ResourceAllocation> allocs = new HashSet<ResourceAllocation>(); 
            //Iterable<Link> linksItr= path.links().iterator();
                
        Iterator<LambdaResource> lambdaIterator = allocatedLambdas.iterator();
        for (int i =0; i<allocatedLambdas.size();i++){
            if (lambdaIterator.hasNext()) {
                allocs.add(new LambdaResourceAllocation(lambdaIterator.next()));
            } else {
                //log.info("Failed to allocate lambda resource.");
                //return null;
            }
        //break;
        }

        Map<Link, Set<ResourceAllocation>> allocations = new HashMap<Link, Set<ResourceAllocation>>();
        for (Link link : path.links()) {
            allocations.put(link, allocs);
        }

        LinkResourceRequest.Builder request = DefaultLinkResourceRequest.builder(IntentId.valueOf(serviceId),
                                                                                 path.links())
                .addLambdaRequest();//HLK 10.29
        LinkResourceAllocations result =
                new DefaultLinkResourceAllocations(request.build(), allocations);

        this.linkResourceAllocations=result;
    }


}