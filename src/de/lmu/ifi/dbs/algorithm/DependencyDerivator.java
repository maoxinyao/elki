package de.lmu.ifi.dbs.algorithm;

import de.lmu.ifi.dbs.algorithm.result.CorrelationAnalysisSolution;
import de.lmu.ifi.dbs.data.RealVector;
import de.lmu.ifi.dbs.database.Database;
import de.lmu.ifi.dbs.distance.Distance;
import de.lmu.ifi.dbs.linearalgebra.LinearEquation;
import de.lmu.ifi.dbs.linearalgebra.Matrix;
import de.lmu.ifi.dbs.pca.LinearLocalPCA;
import de.lmu.ifi.dbs.utilities.Description;
import de.lmu.ifi.dbs.utilities.QueryResult;
import de.lmu.ifi.dbs.utilities.Util;
import de.lmu.ifi.dbs.utilities.optionhandling.AttributeSettings;
import de.lmu.ifi.dbs.utilities.optionhandling.OptionHandler;
import de.lmu.ifi.dbs.utilities.optionhandling.ParameterException;
import de.lmu.ifi.dbs.utilities.optionhandling.WrongParameterValueException;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Dependency derivator computes quantitativly linear dependencies among
 * attributes of a given dataset based on a linear correlation PCA.
 *
 * @author Arthur Zimek (<a
 *         href="mailto:zimek@dbs.ifi.lmu.de">zimek@dbs.ifi.lmu.de</a>)
 */
public class DependencyDerivator<D extends Distance<D>> extends DistanceBasedAlgorithm<RealVector, D> {

  /**
   * Parameter name for alpha - threshold to discern strong from weak
   * Eigenvectors.
   */
  public static final String ALPHA_P = "alpha";

  /**
   * Default value for alpha.
   */
  public static final double ALPHA_DEFAULT = 0.85;

  /**
   * Description for parameter alpha - threshold to discern strong from weak
   * Eigenvectors.
   */
  public static final String ALPHA_D = "<double>threshold to discern strong from weak Eigenvectors ([0:1)) - default: " + ALPHA_DEFAULT + ". Corresponds to the percentage of variance, that is to be explained by a set of strongest Eigenvectors.";

  /**
   * Parameter for correlation dimensionality.
   */
  public static final String DIMENSIONALITY_P = "corrdim";

  /**
   * Description for parameter correlation dimensionality.
   */
  public static final String DIMENSIONALITY_D = "<int>desired correlation dimensionality (> 0 - number of strong Eigenvectors). This parameter is ignored, if parameter " + ALPHA_P + " is set.";

  /**
   * Parameter for output accuracy (number of fraction digits).
   */
  public static final String OUTPUT_ACCURACY_P = "accuracy";

  /**
   * Default value for output accuracy (number of fraction digits).
   */
  public static final int OUTPUT_ACCURACY_DEFAULT = 4;

  /**
   * Description for parameter output accuracy (number of fraction digits).
   */
  public static final String OUTPUT_ACCURACY_D = "<integer>output accuracy fraction digits (>0) (default: " + OUTPUT_ACCURACY_DEFAULT + ").";

  /**
   * Parameter for size of random sample.
   */
  public static final String SAMPLE_SIZE_P = "sampleSize";

  /**
   * Description for parameter for size of random sample.
   */
  public static final String SAMPLE_SIZE_D = "<int>size (> 0) of random sample to use (default: use of complete dataset).";

  /**
   * Flag for use of random sample.
   */
  public static final String RANDOM_SAMPLE_F = "randomSample";

  /**
   * Description for flag for use of random sample.
   */
  public static final String RANDOM_SAMPLE_D = "flag to use random sample (use knn query around centroid, if flag is not set). Flag is ignored if no sample size is specified.";

  /**
   * Holds alpha.
   */
  protected double alpha;

  /**
   * Holds the correlation dimensionality.
   */
  protected int corrdim;

  /**
   * Holds size of sample.
   */
  protected int sampleSize;

  /**
   * Holds the object performing the pca.
   */
  private LinearLocalPCA pca;

  /**
   * Holds the solution.
   */
  protected CorrelationAnalysisSolution solution;

  /**
   * Number format for output of solution.
   */
  public final NumberFormat NF = NumberFormat.getInstance(Locale.US);

  /**
   * Provides a dependency derivator, setting parameters alpha and output
   * accuracy additionally to parameters of super class.
   */
  public DependencyDerivator() {
    super();
    parameterToDescription.put(ALPHA_P + OptionHandler.EXPECTS_VALUE, ALPHA_D);
    parameterToDescription.put(OUTPUT_ACCURACY_P + OptionHandler.EXPECTS_VALUE, OUTPUT_ACCURACY_D);
    parameterToDescription.put(SAMPLE_SIZE_P + OptionHandler.EXPECTS_VALUE, SAMPLE_SIZE_D);
    parameterToDescription.put(RANDOM_SAMPLE_F, RANDOM_SAMPLE_D);
    parameterToDescription.put(DIMENSIONALITY_P + OptionHandler.EXPECTS_VALUE, DIMENSIONALITY_D);
    optionHandler = new OptionHandler(parameterToDescription, this.getClass().getName());
  }

  /**
   * @see Algorithm#getDescription()
   */
  public Description getDescription() {
    return new Description("DependencyDerivator", "Deriving numerical inter-dependencies on data", "Derives an equality-system describing dependencies between attributes in a correlation-cluster", "unpublished");
  }

  /**
   * Calls {@link #runInTime(Database, Integer) run(db,null)}.
   *
   * @see AbstractAlgorithm#runInTime(Database)
   */
  protected void runInTime(Database<RealVector> db) throws IllegalStateException {
    runInTime(db, null);
  }

  /**
   * Runs the pca with the specified correlation dimensionality.
   *
   * @param db                        the database
   * @param correlationDimensionality the desired correlation dimensionality - may remain null, then
   *                                  the parameter setting is used as usual
   * @throws IllegalStateException
   */
  public void runInTime(Database<RealVector> db, Integer correlationDimensionality) throws IllegalStateException {
    if (isVerbose()) {
      System.out.println("retrieving database objects...");
    }
    List<Integer> dbIDs = new ArrayList<Integer>();
    for (Iterator<Integer> idIter = db.iterator(); idIter.hasNext();) {
      dbIDs.add(idIter.next());
    }
    RealVector centroidDV = Util.centroid(db, dbIDs);
    List<Integer> ids;
    if (this.sampleSize >= 0) {
      if (optionHandler.isSet(RANDOM_SAMPLE_F)) {
        ids = db.randomSample(this.sampleSize, 1);
      }
      else {
        List<QueryResult<D>> queryResults = db.kNNQueryForObject(centroidDV, this.sampleSize, this.getDistanceFunction());
        ids = new ArrayList<Integer>(this.sampleSize);
        for (QueryResult<D> qr : queryResults) {
          ids.add(qr.getID());
        }
      }
    }
    else {
      ids = dbIDs;
    }
    if (isVerbose()) {
      System.out.println("PCA...");
    }
    if (correlationDimensionality != null) {
      pca.run(ids, db, correlationDimensionality);
    }
    else if (!optionHandler.isSet(ALPHA_P) && optionHandler.isSet(DIMENSIONALITY_P)) {
      pca.run(ids, db, corrdim);
    }
    else {
      pca.run(ids, db, alpha);
    }

    Matrix weakEigenvectors = pca.getEigenvectors().times(pca.getSelectionMatrixOfWeakEigenvectors());

    Matrix transposedWeakEigenvectors = weakEigenvectors.transpose();
    if (isVerbose()) {
      System.out.println("strong Eigenvectors:");
      System.out.println(pca.getEigenvectors().times(pca.getSelectionMatrixOfStrongEigenvectors()).toString(NF));
      System.out.println("transposed weak Eigenvectors:");
      System.out.println(transposedWeakEigenvectors.toString(NF));
      System.out.println("Eigenvalues:");
      System.out.println(Util.format(pca.getEigenvalues(), " , ", 2));
    }
    Matrix centroid = centroidDV.getColumnVector();
    Matrix B = transposedWeakEigenvectors.times(centroid);
    if (isVerbose()) {
      System.out.println("Centroid:");
      System.out.println(centroid);
      System.out.println("tEV * Centroid");
      System.out.println(B);
    }

    Matrix gaussJordan = new Matrix(transposedWeakEigenvectors.getRowDimension(), transposedWeakEigenvectors.getColumnDimension() + B.getColumnDimension());
    gaussJordan.setMatrix(0, transposedWeakEigenvectors.getRowDimension() - 1, 0, transposedWeakEigenvectors.getColumnDimension() - 1, transposedWeakEigenvectors);
    gaussJordan.setMatrix(0, gaussJordan.getRowDimension() - 1, transposedWeakEigenvectors.getColumnDimension(), gaussJordan.getColumnDimension() - 1, B);

    if (isVerbose()) {
      System.out.println("Gauss-Jordan-Elimination of " + gaussJordan.toString(NF));
      Iterator<Integer> it = db.iterator();
      while (it.hasNext()) {
        Integer id = it.next();
        RealVector dv = db.get(id);
        double[][] values = new double[dv.getDimensionality()][1];
        for (int i = 1; i <= dv.getDimensionality(); i++) {
          values[i - 1][0] = dv.getValue(i).doubleValue();
        }
      }
    }

    double[][] a = new double[transposedWeakEigenvectors.getRowDimension()][transposedWeakEigenvectors.getColumnDimension()];
    double[][] we = transposedWeakEigenvectors.getArray();
    double[] b = B.getColumn(0).getRowPackedCopy();
    System.arraycopy(we, 0, a, 0, transposedWeakEigenvectors.getRowDimension());

    LinearEquation lq = new LinearEquation(a, b);
    lq.solveByTotalPivotSearch();
    Matrix solution = lq.getEquationMatrix();

//      System.out.println("gaussJordanElimination ");
//      System.out.println(gaussJordan.gaussJordanElimination().toString(NF));
//      System.out.println("exact gaussJordanElimination");
//      System.out.println(gaussJordan.exactGaussJordanElimination().toString(NF));
//    Matrix solution =gaussJordan.gaussJordanElimination();
//    Matrix solution = gaussJordan.exactGaussJordanElimination();

    Matrix strongEigenvectors = pca.getEigenvectors().times(pca.getSelectionMatrixOfStrongEigenvectors());
    this.solution = new CorrelationAnalysisSolution(solution, db, strongEigenvectors, centroid, NF);

    if (isVerbose()) {
      System.out.println("Solution:");
      System.out.println("Standard deviation " + this.solution.getStandardDeviation());
      System.out.println(solution.toString(NF));
    }
  }

  /**
   * @see Algorithm#getResult()
   */
  public CorrelationAnalysisSolution getResult() {
    return solution;
  }

  /**
   * @see de.lmu.ifi.dbs.utilities.optionhandling.Parameterizable#setParameters(String[])
   */
  public String[] setParameters(String[] args) throws ParameterException {
    String[] remainingParameters = super.setParameters(args);

    // alpha or dim
    if (optionHandler.isSet(ALPHA_P)) {
      String alphaString = optionHandler.getOptionValue(ALPHA_P);
      try {
        alpha = Double.parseDouble(alphaString);
        if (alpha < 0 || alpha >= 1) {
          throw new WrongParameterValueException(ALPHA_P, alphaString, ALPHA_D);
        }
      }
      catch (NumberFormatException e) {
        throw new WrongParameterValueException(ALPHA_P, alphaString, ALPHA_D, e);
      }
    }
    else if (optionHandler.isSet(DIMENSIONALITY_P)) {
      String corrDimString = optionHandler.getOptionValue(DIMENSIONALITY_P);
      try {
        corrdim = Integer.parseInt(corrDimString);
        if (corrdim < 0) {
          throw new WrongParameterValueException(DIMENSIONALITY_P, corrDimString, DIMENSIONALITY_D);
        }
      }
      catch (NumberFormatException e) {
        throw new WrongParameterValueException(DIMENSIONALITY_P, corrDimString, DIMENSIONALITY_D, e);
      }
    }
    else {
      alpha = ALPHA_DEFAULT;
    }

    // accuracy
    int accuracy;
    if (optionHandler.isSet(OUTPUT_ACCURACY_P)) {
      String accuracyString = optionHandler.getOptionValue(OUTPUT_ACCURACY_P);
      try {
        accuracy = Integer.parseInt(accuracyString);
        if (accuracy < 0) {
          throw new WrongParameterValueException(OUTPUT_ACCURACY_P, accuracyString, OUTPUT_ACCURACY_D);
        }
      }
      catch (NumberFormatException e) {
        throw new WrongParameterValueException(OUTPUT_ACCURACY_P, accuracyString, OUTPUT_ACCURACY_D, e);
      }
    }
    else {
      accuracy = OUTPUT_ACCURACY_DEFAULT;
    }
    NF.setMaximumFractionDigits(accuracy);
    NF.setMinimumFractionDigits(accuracy);

    // sample size
    if (optionHandler.isSet(SAMPLE_SIZE_P)) {
      String sampleSizeString = optionHandler.getOptionValue(SAMPLE_SIZE_P);
      try {
        int sampleSize = Integer.parseInt(sampleSizeString);
        if (sampleSize < 0) {
          throw new WrongParameterValueException(SAMPLE_SIZE_P, sampleSizeString, SAMPLE_SIZE_D);
        }
      }
      catch (NumberFormatException e) {
        throw new WrongParameterValueException(SAMPLE_SIZE_P, sampleSizeString, SAMPLE_SIZE_D, e);
      }
    }
    else {
      sampleSize = -1;
    }

    // pca
    pca = new LinearLocalPCA();
    return pca.setParameters(remainingParameters);
  }

  /**
   * Returns the parameter setting of this algorithm.
   *
   * @return the parameter setting of this algorithm
   */
  public List<AttributeSettings> getAttributeSettings() {
    List<AttributeSettings> attributeSettings = super.getAttributeSettings();
    AttributeSettings mySettings = attributeSettings.get(0);

    if (optionHandler.isSet(ALPHA_P) || !optionHandler.isSet(DIMENSIONALITY_P))
      mySettings.addSetting(ALPHA_P, Double.toString(alpha));

    else if (optionHandler.isSet(DIMENSIONALITY_P))
      mySettings.addSetting(DIMENSIONALITY_P, Integer.toString(corrdim));

    if (optionHandler.isSet(SAMPLE_SIZE_P))
      mySettings.addSetting(SAMPLE_SIZE_P, Integer.toString(sampleSize));

    attributeSettings.addAll(pca.getAttributeSettings());
    return attributeSettings;
  }
}
