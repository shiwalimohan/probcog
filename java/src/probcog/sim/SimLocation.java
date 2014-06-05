package probcog.sim; // XXX - Should it go here? XXX Probably not

import java.awt.Color;
import java.io.IOException;
import java.util.*;

import april.jmat.*;
import april.sim.*;
import april.util.*;
import april.vis.*;

import probcog.classify.Classifications;
import probcog.classify.Features.FeatureCategory;
import probcog.perception.*;
import probcog.util.*;

public class SimLocation extends SimObjectPC{
    private String name;
    protected double[] lwh = new double[]{1, 1, 1};

    public SimLocation(SimWorld sw)
    {
    	super(sw);
    }

    public VisObject getVisObject()
    {
        ArrayList<Object> objs = new ArrayList<Object>();
        
        objs.add(LinAlg.scale(scale));

        // if the door is open, draw an open box
        if(currentState.containsKey("door") && currentState.get("door").equals("open")) {
        	objs.add(new VisChain(LinAlg.translate(-0.5,0,0), 
        						LinAlg.rotateY(Math.PI/2),
        						LinAlg.scale(0.5),
        						new VzRectangle(new VzMesh.Style(color))));
        	objs.add(new VisChain(LinAlg.translate(0.5,0,0), 
								LinAlg.rotateY(Math.PI/2),
								LinAlg.scale(0.5),
								new VzRectangle(new VzMesh.Style(color))));
        	objs.add(new VisChain(LinAlg.translate(0,0.5,0), 
								LinAlg.rotateX(Math.PI/2),
								LinAlg.scale(0.5),
								new VzRectangle(new VzMesh.Style(color))));
        	objs.add(new VisChain(LinAlg.translate(0,-0.5,0), 
								LinAlg.rotateX(Math.PI/2),
								LinAlg.scale(0.5),
								new VzRectangle(new VzMesh.Style(color))));
            objs.add(new VisChain(LinAlg.translate(0,0,-0.5),
            					LinAlg.scale(0.5),
                                new VzRectangle(new VzMesh.Style(color))));
        }else 
        	 // The larger box making up the background of the object
            objs.add(new VisChain(LinAlg.translate(0, 0, 0), new VzBox(new VzMesh.Style(color))));
        
        // The name of the location
        objs.add(new VisChain(LinAlg.rotateZ(Math.PI/2), LinAlg.translate(0,0.8,0.5),
                              LinAlg.scale(0.02),
                              new VzText(VzText.ANCHOR.CENTER, String.format("<<black>> %s", name))));

        return new VisChain(objs.toArray());
    }

    public Shape getShape()
    {
    	return new BoxShape(lwh[0] * scale, lwh[1] * scale, lwh[2] * scale);
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public Obj getObj(boolean assignID)
    {
        Obj locObj;
        if(assignID && id < 0) {
            locObj = new Obj(assignID);
            id = locObj.getID();
        }
        else {
            locObj = new Obj(id);
        }
        
        lwh = new double[]{scale, scale, scale};

        double[] pose = LinAlg.matrixToXyzrpy(T);
        locObj.setPose(pose);
        locObj.setCentroid(new double[]{pose[0], pose[1], pose[2]});
        locObj.setBoundingBox(new BoundingBox(LinAlg.scale(lwh, 1), pose));
        locObj.setVisObject(getVisObject());
        locObj.setShape(getShape());
        locObj.setSourceSimObject(this);
        Classifications location = new Classifications();
        location.add(name, 1.0);
        locObj.addClassifications(FeatureCategory.LOCATION, location);

        return locObj;
    }

    public void read(StructureReader ins) throws IOException
    {
    	super.read(ins);

        name = ins.readString();
    }

    public void write(StructureWriter outs) throws IOException
    {
    	super.write(outs);

        outs.writeString(name);
    }

    // Override for SimObject
    public void setRunning(boolean arg0)
    {
    }
}
