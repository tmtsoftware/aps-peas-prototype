package peas.computationassembly.metadata;

public class EnumConstants {

        public static final int FRAME_SOURCE_CCD = 1;
        public static final int FRAME_SOURCE_FILE = 2;

        public static final int PUPIL_MASK_PASSIVE_TILT = 1;
        public static final int PUPIL_MASK_PHASING = 2;
        public static final int PUPIL_MASK_FINE_SCREEN = 3;
        public static final int PUPIL_MASK_UFS = 4;
        public static final int PUPIL_MASK_SUFS = 5;

        public static final int CALC_OPTION_PURE_TILTS = 1;
        public static final int CALC_OPTION_FOCUS_MODE_PISTONS = 2;
        public static final int CALC_OPTION_PURE_TILTS_PLUS_FOCUS_MODE_PISTONS = 3;
        public static final int CALC_OPTION_PURE_FOCUS_MODE = 4;
        public static final int CALC_OPTION_PURE_TILTS_PLUS_OPTIMAL_PISTONS = 5;
        public static final int CALC_OPTION_OPTIMAL_PISTONS = 6;

        public static final int FRAME_SCALE_ROTATION_REMOVAL_NO = 1;
        public static final int FRAME_SCALE_ROTATION_REMOVAL_YES = 2;
        public static final int FRAME_SCALE_ROTATION_REMOVAL_PROMPT = 3;

        public static final int AUTO_CENTER_TELESCOPE_YES = 1;
        public static final int AUTO_CENTER_TELESCOPE_NO = 2;
        public static final int AUTO_CENTER_TELESCOPE_PROMPT = 3;

        public static final int AUTO_CENTER_PUPIL_YES = 1;
        public static final int AUTO_CENTER_PUPIL_NO = 2;
        public static final int AUTO_CENTER_PUPIL_PROMPT = 3;

        public static final int AUTO_CENTER_PUPIL_MECH_COARSE = 1;
        public static final int AUTO_CENTER_PUPIL_MECH_FINE = 2;
        public static final int AUTO_CENTER_PUPIL_MECH_AUTO = 3;
        public static final int AUTO_CENTER_PUPIL_MECH_PROMPT = 4;

        public static final int AUTO_SUFS_POINT_TEL_YES = 1;
        public static final int AUTO_SUFS_POINT_TEL_NO = 2;
        public static final int AUTO_SUFS_POINT_TEL_PROMPT = 3;

        public static final int AUTO_SEND_ACT_DELTAS_YES = 1;
        public static final int AUTO_SEND_ACT_DELTAS_NO = 2;
        public static final int AUTO_SEND_ACT_DELTAS_PROMPT = 3;

        public static final int AUTO_SEND_M2_ACT_DELTAS_YES = 1;
        public static final int AUTO_SEND_M2_ACT_DELTAS_NO = 2;
        public static final int AUTO_SEND_M2_ACT_DELTAS_PROMPT = 3;

        public static final int AUTO_TAKE_REF_MAPS_YES = 1;
        public static final int AUTO_TAKE_REF_MAPS_NO = 2;
        public static final int AUTO_TAKE_REF_MAPS_PROMPT = 3;

        public static final int SPOT_TYPE_INTERIOR = 1;
        public static final int SPOT_TYPE_PERIPHERAL = 2;

        public static final int MISSING_SPOT_TYPE_NOT_EXPECTED = 0;
        public static final int MISSING_SPOT_TYPE_NOT_FOR_ANALYSIS = 1;
        public static final int MISSING_SPOT_TYPE_USE = 2;


        public static final double PI = 3.14159265;

        public static final double DEG2RAD = 2 * PI / 360.0;

        public static final long MS_PER_HOUR = 1000 * 60 * 60;

        public static final int FIND_CENT_STATUS_SUCCESS = 0;
        //public static final int FIND_CENT_STATUS_?? = 1001;
        //public static final int FIND_CENT_STATUS_?? = 1003;
        //public static final int FIND_CENT_STATUS_?? = 1004;
        //public static final int FIND_CENT_STATUS_?? = 1005;
        //public static final int FIND_CENT_STATUS_?? = 1006;
        //public static final int FIND_CENT_STATUS_?? = 1007;
        //public static final int FIND_CENT_STATUS_?? = 1008;
        //public static final int FIND_CENT_STATUS_?? = 1009;
        //public static final int FIND_CENT_STATUS_?? = 1010;
        public static final int FIND_CENT_STATUS_GAUSS_FALLBACK_X = 1011;
        public static final int FIND_CENT_STATUS_GAUSS_FALLBACK_Y = 1012;
        public static final int FIND_CENT_STATUS_GAUSS_FIT_FAILED_FALLBACK = 1013;
        public static final int FIND_CENT_STATUS_NON_LINEAR = 1014;
        public static final int FIND_CENT_STATUS_NOT_PERFORMED = -1;


        public static final int CALC_M2_METHOD_RAY_TRACE = 1;
        public static final int CALC_M2_METHOD_ZERNIKE = 2;

        public static final float METERS_TO_MM = 1000.0f;
        public static final float METERS_TO_UM = 1000000.0f;
        public static final float RADIANS_TO_ARCSEC = 206265.0f;
        public static final float MICRONS_TO_MM = 1.0f/1000.0f;
        public static final float NM_TO_MICRONS = 1.0f/1000.0f;
        public static final float MICRONS_TO_NM = 1000.0f;

        public static final int ADVANCED_VIEW_ENGINEERING = 1;
        public static final int ADVANCED_VIEW_ADMINISTRATION = 2;

        public static final int TEMPLATE_CENTROID_CALC_METHOD_FIND_CENT = 1;
        public static final int TEMPLATE_CENTROID_CALC_METHOD_IDEAL = 2;

}
