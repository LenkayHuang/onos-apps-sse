package org.onosproject.sse; 
import java.util.Map; 
import java.util.Set; 
 
import org.onosproject.net.Link; 
import org.onosproject.net.resource.link.LambdaResource; 
 
public class SseOpticalLambdaLoad { 
	private int time; 
	private Link link; 
	private int lambdaNum; 
	private Set<Integer> serviceSet; 
	//private Set<Integer> serviceSet; 
 
	public SseOpticalLambdaLoad(int time, Link link, int lambdaNum, Set<Integer> serviceSet) { 
		super(); 
		this.time = time; 
		this.link = link; 
		this.serviceSet = serviceSet; 
	} 
 
	public int getTime() { 
		return time; 
	} 
 
	public void setTime(int time) { 
		this.time = time; 
	} 
	public Link getLink() { 
		return link; 
	} 
 
	public void setLink(Link link) { 
		this.link = link; 
	} 
 
	public void setServiceSet(Set<Integer> serviceSet) { 
		this.serviceSet = serviceSet; 
	} 
	public Set<Integer>  getServiceSet() { 
		return serviceSet; 
	} 
 
	public int getLambdaNum(){ 
		return lambdaNum; 
	} 
	public void setLambdaNum(int lambdaNum){ 
		this.lambdaNum=lambdaNum; 
	} 
} 
