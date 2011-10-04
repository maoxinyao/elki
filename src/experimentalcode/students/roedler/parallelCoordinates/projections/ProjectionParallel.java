package experimentalcode.students.roedler.parallelCoordinates.projections;

import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.elki.visualization.projections.Projection;

public interface ProjectionParallel extends Projection {

  public double getAxisHeight();
  
  public double getDist();
  
  public double getXpos(int dim);
  
  public boolean isVisible(int dim);
  
  public double getMarginX();
  
  public double getMarginY();
  
  public void setVisible(boolean vis, int dim);
  
  public int getVisibleDimensions();
  
  public int getFirstVisibleDimension();
  
  public double projectDimension(int dim, double value);
  
  public int getLastVisibleDimension();
  
  public int getLastVisibleDimension(int dim);
  
  public int getNextVisibleDimension(int dim);
  
  public void swapDimensions(int a, int b);
  
  public void shiftDimension(int dim, int rn);
  
  public int getDimensionNumber(int pos);
  
  public Vector sortDims(Vector s);
}