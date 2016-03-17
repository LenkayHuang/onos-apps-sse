package org.onosproject.sse; 
import java.util.Map; 
import java.util.Set; 
 
import org.onosproject.net.Link; 
import org.onosproject.net.resource.link.LambdaResource; 
 
public class CandidateService { 
	private Set<Integer> timeSet; 
	private Set<Link> linkSet; 
	//private Set<Long> serviceSet; 
	//private Set<Integer> serviceSet; 
 
	public CandidateService(Set<Integer> timeSet, Set<Link> linkSet) { 
		super(); 
		this.timeSet = timeSet; 
		this.linkSet = linkSet; 
	} 
 
	public Set<Integer>  getTimeSet() { 
		return timeSet; 
	} 
 
	public void setTimeSet(Set<Integer> timeSet) { 
		this.timeSet = timeSet; 
	} 
	public Set<Link> getLinkSet() { 
		return linkSet; 
	} 
 
	public void setLinkSet(Set<Link> linkSet) { 
		this.linkSet = linkSet; 
	} 
} 
