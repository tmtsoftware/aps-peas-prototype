package peas.computationassembly;

import org.tmt.aps.peas.lang.interop.*;
import csw.framework.models.JCswContext;
import csw.logging.api.javadsl.ILogger;
import peas.computationassembly.metadata.FloatWrapper;
import peas.computationassembly.metadata.EnumConstants;

public class AlgorithmLibrary {

    private final ILogger log;

    public AlgorithmLibrary(JCswContext cswCtx) {
        this.log = cswCtx.loggerFactory().getLogger(getClass());
    }
    public void colorStep(int stepCount, float stepSizeNm, float[][] colorSteps) throws Exception {

        JcolorStep jcolorStep = new JcolorStep();
        RetVal retVal = new RetVal();

        int arg1 = 5;
        float arg2 = 324.3f;
        float[][] arg3 = new float[arg1+1][3];

        Object[] result0 = jcolorStep.jcolorStep(retVal, arg1, arg2, arg3);

        System.out.println("stepCount = " + arg1 + ", stepSizeNm = " + arg2 + ", colorSteps = ");
        printMatrix(arg3);

        Object[] result = jcolorStep.jcolorStep(retVal, stepCount, stepSizeNm, colorSteps);

        if (retVal.getCode() > 0) {
            throw new Exception("colorStep calcuation error. " + retVal + ".  ");
        }
    }
    public static void printMatrix(float[][] matrix) {
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                System.out.printf("%10.3f ", matrix[i][j]);
            }
            System.out.println();
        }
    }

    // method call with formal parameter names
    public void ttOffsetsToActs(float[] xActPos, float[] yActPos, float secPerPix,
       float[] centroidOffsetsX, float[] centroidOffsetsY, int[] mirrorConfig, float[] xOffsetsOut, float[] yOffsetsOut,
       float[][] desiredActDeltas) throws Exception {

        log.info("IN TT OFFSETS TO ACTS");

        JttOffsetsToActs jttOffsetsToActs = new JttOffsetsToActs();
        RetVal retVal = new RetVal();

        double t1 = System.nanoTime();

        // input vars (mostly just shifting from Algorithm Library Formal param names to Fortran names
        float[] x_act_pos = xActPos;
        float[] y_act_pos = yActPos;
        float secperpix = secPerPix;
        int[] mirror_config = mirrorConfig;
        float[] x_offsets = centroidOffsetsX;
        float[] y_offsets = centroidOffsetsY;

        // output arrays
        float[] desired_act_deltas = new float[xActPos.length];  // not final output var, need to pre-allocate
        float[] x_offsets_out = xOffsetsOut;  // pre-allocated
        float[] y_offsets_out = yOffsetsOut;  // pre-allocated

        // fortran call with slightly different parameters
        Object output[] = jttOffsetsToActs.jttOffsetsToActs(retVal, x_act_pos, y_act_pos, secperpix, x_offsets, y_offsets,
                mirror_config, x_offsets_out, y_offsets_out, desired_act_deltas);

        if (retVal.getCode() > 0) {
            throw new Exception("\"Tip/Tilt Offsets to Actuator Calculation Error. " + retVal + ".  ");
        }

        // move fortran output params to method formal params
        xOffsetsOut = x_offsets_out;
        yOffsetsOut = y_offsets_out;

        // store fi_param values into preallocated desiredActDeltas
        for (int i = 0; i<xActPos.length/3; i++) {
            desiredActDeltas[i][0] = desired_act_deltas[i*3 + 0];
            desiredActDeltas[i][1] = desired_act_deltas[i*3 + 1];
            desiredActDeltas[i][2] = desired_act_deltas[i*3 + 2];
        }

    }

    public void decomposeActs(float[][] desiredActDeltas, float[] tipTiltActs, float[] pistonActs) throws Exception {

        log.info("in decomposeActs");
        JdecomposeActs jdecomposeActs = new JdecomposeActs();
        RetVal retVal = new RetVal();

        float[] actPos = flatten2dArray(desiredActDeltas, 1);

        // output arrays

        float[] act_tt = tipTiltActs;

        float[] act_p = pistonActs;



        Object output[] = jdecomposeActs.jdecomposeActs(retVal, actPos, act_tt, act_p);

        if (retVal.getCode() > 0) {
            throw new Exception("Decompose Actuators Calculation Error:  " + retVal);
        }

        log.info("tipTiltActs: " + printArrayRecursive(tipTiltActs, 0) + ", pistonActs: " + printArrayRecursive(pistonActs, 0));

    }


    public static float[] flatten2dArray(float[][] input, int fastIndex) {
        float[] result = new float[input.length * input[0].length];
        int k = 0;
        if (fastIndex == 0) {
            for (int i = 0; i < input[0].length; i++) {
                for (int j = 0; j < input.length; j++) {
                    result[k++] = input[j][i];
                }
            }
        } else {
            for (int i = 0; i < input.length; i++) {
                for (int j = 0; j < input[0].length; j++) {
                    result[k++] = input[i][j];
                }
            }
        }
        return result;
    }

    public static float[][] expandTo2dArray(float[] input, int minorIndexSize) {
        float[][] result = new float[input.length / minorIndexSize][minorIndexSize];
        for (int i = 0; i < input.length / minorIndexSize; i++) {
            for (int j = 0; j < minorIndexSize; j++) {
                result[i][j] = input[i * minorIndexSize + j];
            }
        }
        return result;
    }

    /**
     * Utility method that returns an array of usable spot flags.  A spot is usable if it was a missing spot type 'Use for analysis' and the
     * findCentStatus for that spot indicates that the spot was found.
     * @param missingSpotFlags array of flags indicating missing spot type (missing from find and identify, use for analysis)
     * @param findCentStatusList an array of flags indicating if/how this spot was found during find and identify
     */
    public int[] goodCentroidsFound(int[] missingSpotFlags, int[] findCentStatusList) {
        int[] found = new int[findCentStatusList.length];

        for (int i=0; i<found.length; i++) {
            boolean isGood = (findCentStatusList[i] == EnumConstants.FIND_CENT_STATUS_SUCCESS ||
                    findCentStatusList[i] == EnumConstants.FIND_CENT_STATUS_GAUSS_FIT_FAILED_FALLBACK ||
                    findCentStatusList[i] == EnumConstants.FIND_CENT_STATUS_GAUSS_FALLBACK_X ||
                    findCentStatusList[i] == EnumConstants.FIND_CENT_STATUS_GAUSS_FALLBACK_Y) &&
                    missingSpotFlags[i] == EnumConstants.MISSING_SPOT_TYPE_USE;
            found[i] = isGood ? 1 : 0;
        }

        return found;

    }

    public void calculatePupilRegError(int fractionalIntensityCalcMethod, float[] intensities, int numSpots,
                                       float[] peripheralSpotPerp, float[] peripheralSpotParallel, float[] peripheralSpotTheta, float aHex, float spotDiameter, int[] nspotTypes,
                                       int[] missingSpotFlags, int[] findCentStatusList, float pupilRegistrationOffsetX, float pupilRegistrationOffsetY, FloatWrapper regErrorX,
                                       FloatWrapper regErrorY, FloatWrapper regErrorPhi, FloatWrapper regErrorApproxX, FloatWrapper regErrorApproxY, FloatWrapper regErrorApproxPhi,
                                       FloatWrapper regScaleError)
            throws Exception {


        JcalculatePupilRegError jcalculatePupilRegError = new JcalculatePupilRegError();
        RetVal retVal = new RetVal();

        // spots that can be used (found without errors and should be used for analysis)
        int[] good_spots = 	goodCentroidsFound(missingSpotFlags, findCentStatusList);


        // expand peripheral spot arrays to be subimages length long
        float[] allSpotPerp = new float[nspotTypes.length];
        float[] allSpotParallel = new float[nspotTypes.length];
        float[] allSpotTheta = new float[nspotTypes.length];

        for (int i=0; i<35; i++) {
            allSpotPerp[nspotTypes.length - 35 + i] =  peripheralSpotPerp[i];
            allSpotParallel[nspotTypes.length - 35 + i] = peripheralSpotParallel[i];
            allSpotTheta[nspotTypes.length - 35 + i] =  peripheralSpotTheta[i];
        }


        Object[] output = jcalculatePupilRegError.jcalculatePupilRegError(retVal, fractionalIntensityCalcMethod,
                intensities, good_spots, nspotTypes, allSpotTheta,
                allSpotPerp, allSpotParallel, aHex, spotDiameter);

        if (retVal.getCode() > 0) {
            throw new Exception("calculate pupil reg error failed, status code = " + retVal.getCode());
        }

        // we need a consistent way to return output parameters
        regErrorX.value = (Float) output[0] - (pupilRegistrationOffsetX / EnumConstants.METERS_TO_MM);
        regErrorY.value = (Float) output[1] - (pupilRegistrationOffsetY / EnumConstants.METERS_TO_MM);
        regErrorPhi.value = (Float) output[2];
        regErrorApproxX.value = (Float) output[3] - (pupilRegistrationOffsetX / EnumConstants.METERS_TO_MM);
        regErrorApproxY.value = (Float) output[4] - (pupilRegistrationOffsetY / EnumConstants.METERS_TO_MM);
        regErrorApproxPhi.value = (Float) output[5];
        regScaleError.value = (Float) output[6];


    }

    public void testApsFrame(float[][] frame, int[] numFilledBoxes) throws Exception {

        JtestApsFrame jtestApsFrame = new JtestApsFrame();
        RetVal retVal = new RetVal();

        float[][] arg1 = frame;

        Object[] result0 = jtestApsFrame.jtestApsFrame(retVal, arg1);

        numFilledBoxes[0] = (Integer)result0[0];

        if (retVal.getCode() > 0) {
            throw new Exception("colorStep calcuation error. " + retVal + ".  ");
        }

    }



    private StringBuffer printArrayRecursive(Object value, int indentLevel) {

        StringBuffer buf = new StringBuffer();

        Class<?> clazz = value.getClass();

        if (!clazz.isArray()) {
            buf.append(value);
            return buf;
        }

        int length = java.lang.reflect.Array.getLength(value);

        buf.append("[");
        for (int i = 0; i < length; i++) {

            Object element = java.lang.reflect.Array.get(value, i);

            if (element != null && element.getClass().isArray()) {
                buf.append("\n");
                printIndent(buf,indentLevel + 1);
                printArrayRecursive(element, indentLevel + 1);
            } else {
                buf.append(element);
            }

            if (i < length - 1) {
                buf.append(", ");
            }
        }
        buf.append("]");

        return buf;
    }

    private void printIndent(StringBuffer buf, int level) {
        for (int i = 0; i < level; i++) {
            buf.append("  ");
        }
    }


    }