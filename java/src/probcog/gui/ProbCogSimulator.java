package probcog.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import april.config.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.sim.*;
import april.util.*;
import april.vis.*;
import april.vis.VisCameraManager.CameraPosition;
import april.vis.VzMesh.Style;

import probcog.perception.*;
import probcog.classify.*;
import probcog.classify.Features.FeatureCategory;
// import abolt.collision.ShapeToVisObject;
// import abolt.sim.SimSensable;
// import abolt.util.SimUtil;
// import abolt.objects.SensableManager;
import probcog.vis.*;

public class ProbCogSimulator implements VisConsole.Listener
{
	// Sim stuff
    SimWorld world;
    Simulator sim;

    // Vis stuff
    VisWorld vw;
    VisCanvas vc;
    VisLayer vl;
    VisConsole console;

    // State
    Object selectionLock = new Object();
    int selectedId = -1;
    SelectionAnimation animation = null;

    enum ViewType{
    	POINT_CLOUD, SOAR
    };
    enum ClickType{
    	SELECT, CHANGE_ID, VISIBLE
    };

    ViewType viewType;
    ClickType clickType;

    private Tracker tracker;

    public ProbCogSimulator(GetOpt opts, Tracker tracker_)
    {
        tracker = tracker_;

		vw = new VisWorld();
		vl = new VisLayer(vw);
	    vc = new VisCanvas(vl);
	    vc.setTargetFPS(opts.getInt("fps"));

	    console = new VisConsole(vw, vl, vc);
	    console.addListener(this);

	    // Customized event handling
	    vl.addEventHandler(new BoltEventHandler());

        loadWorld(opts);
        sim = new Simulator(vw, vl, console, world);
        viewType = ViewType.SOAR;
        clickType = ClickType.SELECT;

        // Render updates about the world
        RenderThread rt = new RenderThread();
        rt.start();
	}

    public VisWorld.Buffer getVisWorld()
    {
        return vw.getBuffer("arm_cylinders");
    }
    public SimWorld getWorld()
    {
    	return world;
    }
    public VisCanvas getCanvas()
    {
    	return vc;
    }
    public VisLayer getLayer()
    {
    	return vl;
    }
    public int getSelectedId()
    {
    	return selectedId;
    }

    private void loadWorld(GetOpt opts)
    {
    	try {
            Config config = new Config();
            if (opts.wasSpecified("sim-config"))
                config = new ConfigFile(EnvUtil.expandVariables(opts.getString("sim-config")));

            if (opts.getString("world").length() > 0) {
                String worldFilePath = EnvUtil.expandVariables(opts.getString("world"));
                world = new SimWorld(worldFilePath, config);
            } else {
                world = new SimWorld(config);
            }
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
            ex.printStackTrace();
            return;
        }
    	world.setRunning(true);
    }

    public void addToMenu(JMenuBar menuBar)
    {
    	JMenu simMenu = new JMenu("Simulator");

    	addViewTypeMenu(simMenu);
    	simMenu.addSeparator();
    	addClickTypeMenu(simMenu);

    	menuBar.add(simMenu);
    }

    private void addClickTypeMenu(JMenu menu)
    {
    	menu.add(new JLabel("Change Selection Mode"));
    	ButtonGroup clickGroup = new ButtonGroup();

    	JRadioButtonMenuItem select = new JRadioButtonMenuItem("Select Object");
    	select.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				setClickType(ClickType.SELECT);
			}
    	});
    	clickGroup.add(select);
    	menu.add(select);

    	JRadioButtonMenuItem visiblity = new JRadioButtonMenuItem("Toggle Visibility");
    	visiblity.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				setClickType(ClickType.VISIBLE);
			}
    	});
    	clickGroup.add(visiblity);
    	menu.add(visiblity);


    	JRadioButtonMenuItem changeId = new JRadioButtonMenuItem("Change Id");
    	changeId.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				setClickType(ClickType.CHANGE_ID);
			}
    	});
    	clickGroup.add(changeId);
    	menu.add(changeId);
    }

    private void addViewTypeMenu(JMenu menu)
    {
    	menu.add(new JLabel("Change Simulator View"));
    	ButtonGroup viewGroup = new ButtonGroup();

    	JRadioButtonMenuItem pointView = new JRadioButtonMenuItem("Point Cloud View");
    	pointView.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				setView(ViewType.POINT_CLOUD);
			}
    	});
    	viewGroup.add(pointView);
    	menu.add(pointView);

    	JRadioButtonMenuItem soarView = new JRadioButtonMenuItem("Soar View");
    	soarView.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				setView(ViewType.SOAR);
			}
    	});
    	viewGroup.add(soarView);
    	menu.add(soarView);
    }

    private void setView(ViewType view)
    {
        System.out.println("Set view: "+view);
    	this.viewType = view;
    }

    private void setClickType(ClickType click)
    {
    	this.clickType = click;
    }

	public boolean consoleCommand(VisConsole console, PrintStream out, String command)
    {
        return false;
    }

    public ArrayList<String> consoleCompletions(VisConsole console, String prefix)
    {
        return null;    // Only using start and stop from sim, still
    }

    class BoltEventHandler extends VisEventAdapter
    {
        public int getDispatchOrder()
        {
            return -20; // Want to beat out the sim, though pass events through
        }

        /** Keep track of the last object to be selected in the sim world. */
        public boolean mousePressed(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
        {
            double bestd = Double.MAX_VALUE;
            Object selectedObject = null;

            synchronized(world) {
                synchronized(tracker.stateLock) {
                	for (Obj ob : tracker.getWorldState().values()) {
                        double d = Collisions.collisionDistance(ray.getSource(), ray.getDir(), ob.getShape(), ob.getPoseMatrix());
                        if (d < bestd) {
                            synchronized (selectionLock) {
                            	selectedObject = ob;
                                bestd = d;
                            }
                        }
                    }
                }

                // XXX -- DIE SENSABLES - might want to reconsider later
                // if(bestd == Double.MAX_VALUE){
                // 	HashMap<Integer, SimSensable> sensables = SensableManager.getSingleton().getSensables();
	            //     synchronized(sensables){
	            //     	for (SimSensable sens : sensables.values()) {
	            //     		if(!(sens instanceof SimObject)){
	            //     			continue;
	            //     		}
	            //     		SimObject obj = (SimObject)sens;
	            //             double d = Collisions.collisionDistance(ray.getSource(), ray.getDir(), obj.getShape(), obj.getPose());

	            //             if (d < bestd) {
	            //                 synchronized (selectionLock) {
	            //                 	selectedObject = obj;
	            //                     bestd = d;
	            //                 }
	            //             }
	            //         }
	            //     }
                // }

                if(selectedObject != null){
                	if(selectedObject instanceof Obj){
                		Obj ob = (Obj)selectedObject;
                		switch(clickType){
                		case CHANGE_ID:
                			//if(obj.getInfo().createdFrom != null){  XXX - Not sure what this was doing...
                            ob.setID(Obj.nextID());
                                //}
                			break;
                        case SELECT:
                        	animation = null;
                        	selectedId = ob.getID();
                        	break;
                        case VISIBLE:
                    		ob.setVisible(!ob.isVisible());
                        	break;
                        }
                	}
                    // XXX -- DIE SENSABLES - might want to reconsider later
                    // else if(selectedObject instanceof SimSensable){
                	// 	SimSensable ob = (SimSensable)selectedObject;
                	// 	switch(clickType){
                    //     case SELECT:
                    //     	animation = null;
                    //     	selectedId = ob.getID();
                    //     	break;
                    //     }
                	// }
                }
            }

            return false;
        }
    }

    /** Render BOLT-specific content. */
    class RenderThread extends Thread
    {
        int fps = 20;

        public void run()
        {
            Tic tic = new Tic();
            while (true) {

                double dt = tic.toctic();
                if (animation != null) {
                    VisWorld.Buffer vb = vw.getBuffer("selection");
                    animation.step(dt);
                    vb.addBack(animation);
                    vb.swap();
                } else {
                	vw.getBuffer("selection").clear();
                }

            	CameraPosition camera = vl.cameraManager.getCameraTarget();
        		double[] forward = LinAlg.normalize(LinAlg.subtract(camera.eye, camera.lookat));
        		// Spherical coordinates
                double psi = Math.PI/2.0 - Math.asin(forward[2]);   // psi = Pi/2 - asin(z)
                double theta = Math.atan2(forward[1], forward[0]);  // theta = atan(y/x)
                if(forward[0] == 0 && forward[1] == 0){
                	theta = -Math.PI/2;
                }
                double[][] tilt = LinAlg.rotateX(psi); 				// tilt up or down to face the camera vertically
                double[][] rot = LinAlg.rotateZ(theta + Math.PI/2); // rotate flat to face the camera horizontally
                double[][] faceCamera = LinAlg.matrixAB(rot, tilt);
                VisWorld.Buffer textBuffer = vw.getBuffer("textbuffer");

                synchronized(tracker.stateLock){
                    HashMap<Integer, Obj> worldState = tracker.getWorldState();

                	if(worldState.containsKey(selectedId)){
                		if(animation == null){
                    		Obj selectedObject = worldState.get(selectedId);
                            double[] xyz = LinAlg.resize(LinAlg.matrixToXyzrpy(selectedObject.getPoseMatrix()), 3);
                            double br = Math.abs(selectedObject.getShape().getBoundingRadius());
                            animation = new SelectionAnimation(xyz, br*1.2);
                		}
                	}
                    // XXX -- DIE SENSABLES - might want to reconsider later
                    // else if(SensableManager.getSingleton().getSensables().containsKey(selectedId)){
            		// 	SimSensable obj = SensableManager.getSingleton().getSensables().get(selectedId);
                	// 	if(obj instanceof SimObject && animation == null){
                    //         double[] xyz = LinAlg.resize(LinAlg.matrixToXyzrpy(((SimObject)obj).getPose()), 3);
                    //         double br = Math.abs(((SimObject)obj).getShape().getBoundingRadius());
                    //         animation = new SelectionAnimation(xyz, br*1.2);
                	// 	}
                	// }
                    else {
                		animation = null;
                	}

                    for(Obj ob : tracker.getWorldState().values()){
                    	String labelString = "";
                		String tf="<<monospaced,black,dropshadow=false>>";
                		labelString += String.format("%s%d\n", tf, ob.getID());
                    	if(ob.isVisible()){
                    		for(FeatureCategory cat : FeatureCategory.values()){
                                Classifications cs = ob.getLabels(cat);
                        		labelString += String.format("%s%s:%.2f\n", tf,
                                                             cs.getBestLabel().label,
                                                             cs.getBestLabel().weight);
                    		}
                    	}
                		VzText text = new VzText(labelString);
                		double[] textLoc = new double[]{ob.getPose()[0], ob.getPose()[1], ob.getPose()[2] + .1};
                        textBuffer.addBack(new VisChain(LinAlg.translate(textLoc), faceCamera, LinAlg.scale(.002), text));
                    }
                }
                textBuffer.swap();

                // Object drawing
                drawObjects();
                TimeUtil.sleep(1000/fps);
            }
        }
    }

	public void drawObjects()
    {
		VisWorld.Buffer objectBuffer = vw.getBuffer("objects");
		switch(viewType){
		case POINT_CLOUD:
			drawPointCloud(objectBuffer);
			break;
		case SOAR:
			drawSoarView(objectBuffer);
			break;
		}

		objectBuffer.swap();
	}

	private void drawPointCloud(VisWorld.Buffer buffer)
    {
    	synchronized(tracker.stateLock){
			for(Obj ob : tracker.getWorldState().values()){
				ArrayList<double[]> points = ob.getPointCloud().getPoints();
				if(points != null && points.size() > 0){
	    			VisColorData colors = new VisColorData();
	    			VisVertexData vertexData = new VisVertexData();
	    			for(double[] pt : points){
	    				vertexData.add(new double[]{pt[0], pt[1], pt[2]});
		    			colors.add((int)pt[3]);
	    			}
	    			VzPoints visPts = new VzPoints(vertexData, new VzPoints.Style(colors, 2));
	    			buffer.addBack(visPts);
				}
			}
    	}
	}

	private void drawSoarView(VisWorld.Buffer buffer)
    {
    	synchronized(tracker.stateLock){
			for(Obj ob : tracker.getWorldState().values()){
                // XXX - Pretty sure the below is all related to sim objects - needs to be uncommented and fixed
				// if(ob.getInfo().createdFrom != null){
				// 	ISimBoltObject simObj = obj.getInfo().createdFrom;
				// 	ArrayList<VisObject> visObjs = ShapeToVisObject.getVisObjects(simObj.getAboltShape(), new VzMesh.Style(simObj.getColor()));
				// 	for(VisObject visObj : visObjs){
    			// 		buffer.addBack(new VisChain(simObj.getPose(), visObj));
				// 	}
				// }
                // else {
	    			buffer.addBack(ob.getVisObject());
                    //}
	    	}
    	}
	}

    public void drawVisObjects(String bufferName, ArrayList<VisObject> objects)
    {
    	VisWorld.Buffer buffer = vw.getBuffer(bufferName);
    	for(VisObject obj : objects){
    		buffer.addBack(obj);
    	}
    	buffer.swap();
    }
}
