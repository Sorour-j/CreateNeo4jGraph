package org.eclipse.epsilon.importer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.io.fs.FileUtils;
import org.atlanmod.commons.cache.Cache;
import org.atlanmod.commons.cache.CacheBuilder;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

public class CreateGraph {
	
	
	private Cache<String, Node> cache = CacheBuilder.builder()
			.softValues()
			.build();
	
	private static final String resourceName = "./resources/Grabats-set3.xmi";
	private static final String metamodel = "./resources/JDTAST.ecore";


	private static Path databaseDirectory = null;
	ResourceSet xmiResourceSet = new ResourceSetImpl();
	ResourceSet ecoreResourceSet = new ResourceSetImpl();
	Resource resource;
	ArrayList<String> className = new ArrayList<String>();
	ArrayList<String> refName = new ArrayList<String>();
	//HashMap<String,Node> types = new HashMap<String,Node>();
	
	public String greeting;
	HashMap<Object, Node> visitedObjects = new HashMap<Object, Node>();
	
	// tag::vars[]
	GraphDatabaseService graphDb;
	Node firstNode;
	Node secondNode;
	Relationship relationship;
	ArrayList<RelType> refs = new ArrayList<RelType>();
	private DatabaseManagementService managementService;
	// end::vars[]


	
	// tag::createReltype[]
//	private enum RelTypes implements RelationshipType {
//		KNOWS
//	}
	// end::createReltype[]
	
	public static void main(final String[] args) throws IOException {
		CreateGraph hello = new CreateGraph();
		
	//	String EcoreMetamodel = args[0];
//		String XmiModel = args[1];
		String db = "target/Grabats-set3";
		databaseDirectory = Paths.get(db);
		hello.RegisterEcore(metamodel);
	//	hello.RegisterEcore(EcoreMetamodel);
	//	hello.loadXmi(XmiModel);
		hello.loadXmi(resourceName);
		//hello.printInfo();
		hello.createDb();
		System.out.print("Return");
		//hello.removeData();
		hello.shutDown();
	}

	void createDb() throws IOException {
	//	FileUtils.deleteDirectory(databaseDirectory);
		Node s, t, type;
		// tag::startDb[]
		System.out.println("Starting database ...");
		 managementService = new DatabaseManagementServiceBuilder( databaseDirectory )
			    .setConfig( BoltConnector.enabled, true )
			    .setConfig( HttpConnector.enabled, true )
			    .setConfig( BoltConnector.listen_address, new SocketAddress( "localhost", 7687 ) )
			    .build();
		 
		
		graphDb = managementService.database(DEFAULT_DATABASE_NAME);
	//	registerShutdownHook(managementService);
		// end::startDb[]
		System.out.println("Starting graph creation ...");
		RelType rel = new RelType();
		RelType relType = new RelType();
		try (Transaction tx = graphDb.beginTx()) {

			EList<EObject> objects = resource.getContents();
			while (!objects.isEmpty()) {
				EObject obj = objects.get(0);
				
				if (obj == null) 
					return;
				
				objects.remove(obj);
				if (!obj.eContents().isEmpty())
					objects.addAll(obj.eContents());
				
				String name = obj.eClass().getName();

				if (className.contains(name)) {
					
				
					if (!visitedObjects.keySet().contains(obj)) {
						s = tx.createNode();
						s = setPros(obj, s);
						
						if (!cache.contains(name)) {
							type = tx.createNode();
							type.setProperty("name", name);
							cache.put(name,type);
						}
						else
							type = cache.get(name);
						
							relType.setName("instanceOf");
							s.createRelationshipTo(type, relType);
						
						visitedObjects.put(obj,s);
					}
					else
						s = visitedObjects.get(obj);

					for (EReference ref : obj.eClass().getEAllReferences()) {
						Object f =  obj.eGet(ref);
						
						if (f != null) {
						rel.setName(ref.getName());
						ArrayList<EObject> refs = new ArrayList<EObject>();
						if (f instanceof Collection)
							refs.addAll((Collection<EObject>)f);
						else
							refs.add((EObject) f);
						
						for (EObject r : refs) {
							
							if (!visitedObjects.keySet().contains(r)) {
								t = tx.createNode();
								t = setPros(r, t);
								
								if (!cache.contains(r.eClass().getName())) {
									type = tx.createNode();
									type.setProperty("name", r.eClass().getName());
						
									cache.put(r.eClass().getName(),type);
								}
								else
									type = cache.get(r.eClass().getName());
								
								relType.setName("instanceOf");
								t.createRelationshipTo(type, relType);
								
//								for (EClass c : r.eClass().getEAllSuperTypes()) {
//									
//									if (!types.containsKey(c.getName())) {
//										type = tx.createNode();
//										type.setProperty("name", c.getName());
//										types.put(c.getName(),type);
//									}
//									else
//										type = types.get(c.getName());
//								
//									relType.setName("subclassOf");
//									t.createRelationshipTo(type, relType);
//								}
							}
							else
								t = visitedObjects.get(r);
							
							s.createRelationshipTo(t, rel);
							visitedObjects.put(r,t);
						}
						}
					}
				}
				
			}
			System.out.println("End!!..");
			
			// tag::transaction[]
			tx.commit();
		}
		// end::transaction[]
	}

	Node setPros(EObject obj,Node s) {
		
		for (EAttribute atr : obj.eClass().getEAllAttributes()) {
			
			if (obj.eGet(atr) != null)
				s.setProperty(atr.getName(),obj.eGet(atr).toString());
		}
		
		s.addLabel(new org.neo4j.graphdb.Label() {

			@Override
			public String name() {
				return obj.eClass().getName();
			}
		});
		for (EClass c : obj.eClass().getEAllSuperTypes()) {
		
		s.addLabel(new org.neo4j.graphdb.Label() {

			@Override
			public String name() {
				return c.getName();
			}
		});
		}
		return s;
	}
	

	void shutDown() {
		System.out.println();
		System.out.println("Shutting down database ...");
		// tag::shutdownServer[]
		managementService.shutdown();
		// end::shutdownServer[]
	}

	// tag::shutdownHook[]
	private static void registerShutdownHook(final DatabaseManagementService managementService) {
		// Registers a shutdown hook for the Neo4j instance so that it
		// shuts down nicely when the VM exits (even if you "Ctrl-C" the
		// running application).
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				managementService.shutdown();
			}
		});
	}
	// end::shutdownHook[]

	void RegisterEcore(String metamodel) {
		System.out.println("Registering Ecore:......");
		ecoreResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
		Resource ecoreResource = ecoreResourceSet
				.createResource(URI.createFileURI(new File(metamodel).getAbsolutePath()));
		try {
			ecoreResource.load(null);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		for (EObject o : ecoreResource.getContents()) {
			EPackage ePackage = (EPackage) o;
			xmiResourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
			for (EClassifier cls : ePackage.getEClassifiers()) {
				className.add(cls.getName());
			}
		}
		System.out.println("Registeration Done!......");
	}

	void loadXmi(String model) {
		System.out.println("Start loading xmi:......");
		xmiResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
		resource = xmiResourceSet.createResource(URI.createFileURI(new File(model).getAbsolutePath()));
		try {
			resource.load(null);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Loading done!......");
	}
	
	void printInfo() {
		
		EList<EObject> objects = resource.getContents();
		while (!objects.isEmpty()) {
			EObject obj = objects.get(0);
			
			if (obj == null) 
				return;
			
			objects.remove(obj);
			if (!obj.eContents().isEmpty())
				objects.addAll(obj.eContents());

			String name = obj.eClass().getName();
			
			System.out.println("Label: " + obj.eClass().getName());
			if(obj.eClass().getName().equals("ClassDeclaration")) {
			for (EAttribute atr : obj.eClass().getEAllAttributes()) {
				//String value = obj.eGet(atr).toString();
				if (obj.eGet(atr) != null)
					System.out.println("Attr Name: "+ atr.getName() +" Attr value: " + obj.eGet(atr).toString());
			}
			for (EReference ref : obj.eClass().getEAllReferences()) {
				Object f =  obj.eGet(ref);
				System.out.println("Relationship Type: " + ref.getName());
				if (f != null) {
		
				ArrayList<EObject> refs = new ArrayList<EObject>();
				
				if (f instanceof Collection)
					refs.addAll((Collection<EObject>)f);
				else
					refs.add((EObject) f);
				
				for (EObject r : refs) {
					 System.out.println("Value Rel : " + r);
				}
				}
				else
					 System.out.println("Value Rel : Null");	
			}
	}
		}
}
//	void printGraph() {
//		for(Node n : nodes) {
//			System.out.println(n + ":::"+ n.getAllProperties());
//			if (n.getId() == 4003)
//				System.out.println(0);
//			for (Relationship r : n.getRelationships())
//			System.out.println(r.getStartNode() + "-" + r.getType() + "->" + r.getEndNode());
//		}
//	}
}
