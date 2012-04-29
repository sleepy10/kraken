package org.krakenapps.sonar.metabase.model;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "sonar_applications")
public class Application {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private int id;

	@ManyToOne
	@JoinColumn(name = "vendor_id")
	private Vendor vendor;

	@Column(nullable = false)
	private String name;

	private String version;

	@ManyToMany
	private Set<Component> components;

	@ManyToMany
	private Set<IpEndPoint> endpoints;
	
	public Application() {
	}
	
	public Application(Vendor vendor, String name, String version, IpEndPoint endpoint)	{
		this.vendor		= vendor;
		this.name		= name;
		this.version	= version;
		
		this.endpoints	= new HashSet<IpEndPoint>();
		this.endpoints.add(endpoint);
	}
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public Vendor getVendor() {
		return vendor;
	}

	public void setVendor(Vendor vendor) {
		this.vendor = vendor;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public Set<Component> getComponents() {
		return components;
	}
	
	public void setComponents(Set<Component> components) {
		this.components = components;
	}

	public void AddEndpoint(IpEndPoint endpoint) {
		this.endpoints.add(endpoint);
	}

	public Set<IpEndPoint> getEndpoints() {
		return this.endpoints;
	}
	
	public void setEndpoints(Set<IpEndPoint> endpoints) {
		this.endpoints = endpoints;
	}

	@Override
	public String toString() {
		return String.format("%s %s %s", vendor.getName(), name, version);
	}
}
