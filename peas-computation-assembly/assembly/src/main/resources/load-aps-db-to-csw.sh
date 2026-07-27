#!/usr/bin/env bash
# =============================================================================
# load-aps-db-to-csw.sh
#
# Loads all 21 APS HOCON files (15 output + 6 input) into the CSW
# Configuration Service under tmt/aps/db/, then sets each one as active.
#
# Prerequisites:
#   - csw-services must be running (location + config services)
#   - csw-config-cli must be installed via: cs install csw-config-cli
#   - Must be logged in as config-admin: csw-config-cli login --username config-admin1
#
# Usage:
#   ./load-aps-db-to-csw.sh [LOCAL_DIR]
#
#   LOCAL_DIR  path to folder containing the 15 .conf files
#              defaults to the script's own directory if not provided
#
# To re-run after files already exist, use the --update flag:
#   ./load-aps-db-to-csw.sh [LOCAL_DIR] --update
# =============================================================================

set -euo pipefail

# ── Arguments ─────────────────────────────────────────────────────────────────
LOCAL_DIR="${1:-$(dirname "$0")}"
UPDATE_MODE=false
if [[ "${2:-}" == "--update" ]]; then
    UPDATE_MODE=true
fi

# ── Config ────────────────────────────────────────────────────────────────────
CSW_DIR="/tmt/aps/db"
COMMENT="APS geometrical database initial load"

# All 21 files to load — 15 geometry outputs + 6 inputs
FILES=(
    # ── 15 output files ───────────────────────────────────────────────────────
    "APS_DB_FS_6_rectangular_array"
    "APS_DB_FS_6_rings_global_xy_scaled"
    "APS_DB_FS_6_rings_local_xy_unscaled"
    "APS_DB_M1CS_modes"
    "APS_DB_PH_2_periph_subaps_vs_segment"
    "APS_DB_PH_params_2_subap"
    "APS_DB_PH_triangles"
    "APS_DB_PH_closure_triples"
    "APS_DB_emult_sing_values"
    "APS_DB_given_1_subaps_find_corres_2"
    "APS_DB_given_2_subaps_find_corres_1"
    "APS_DB_optimal_FI_freqs_8192_6_rings"
    "APS_DB_ref_def_tmt_8192_FS_6"
    "APS_DB_segment_colors"
    "APS_DB_segment_sensor_numbers_2_subap"
    # ── 6 input files ─────────────────────────────────────────────────────────
    "APS_DB_actuators_xy"
    "APS_DB_seg_ctrs"
    "APS_DB_sensor_data"
    "APS_DB_ungapped_vertices"
    "APS_DB_m1cs_sensor_numbering"
    "APS_DB_Amatrix_Leff"
)

# ── Checks ────────────────────────────────────────────────────────────────────
if ! command -v csw-config-cli &> /dev/null; then
    echo "ERROR: csw-config-cli not found. Install it with: cs install csw-config-cli"
    exit 1
fi

if [ ! -d "$LOCAL_DIR" ]; then
    echo "ERROR: Directory not found: $LOCAL_DIR"
    exit 1
fi

# ── Helpers ───────────────────────────────────────────────────────────────────
ok()   { echo "  [OK]  $1"; }
fail() { echo "  [FAIL] $1"; exit 1; }
info() { echo ">>> $1"; }

# ── Login reminder ────────────────────────────────────────────────────────────
echo "============================================================"
echo "  APS DB -> CSW Configuration Service loader (21 files)"
echo "  Local dir : $LOCAL_DIR"
echo "  CSW path  : $CSW_DIR"
echo "  Mode      : $( $UPDATE_MODE && echo UPDATE || echo CREATE )"
echo "============================================================"
echo ""
echo "NOTE: create/setActiveVersion require config-admin role."
echo "If not already logged in, run:"
echo "  csw-config-cli login"
echo ""
echo "This opens a browser window. Log in with:"
echo "  username: config-admin1"
echo "  password: config-admin1"
echo "(dev/test credentials only — not for production)"
echo ""
read -rp "Press Enter to continue, or Ctrl-C to cancel..."
echo ""

# ── Main loop ─────────────────────────────────────────────────────────────────
PASS=0
FAIL=0

for NAME in "${FILES[@]}"; do
    LOCAL_FILE="$LOCAL_DIR/${NAME}.conf"
    CSW_PATH="$CSW_DIR/${NAME}.conf"

    info "Processing: $NAME"

    # Check local file exists
    if [ ! -f "$LOCAL_FILE" ]; then
        echo "  [SKIP] Local file not found: $LOCAL_FILE"
        (( FAIL++ )) || true
        continue
    fi

    # Create or update depending on whether file already exists in CSW
    if $UPDATE_MODE; then
        echo "  Updating $CSW_PATH ..."
        if csw-config-cli update "$CSW_PATH" \
                -i "$LOCAL_FILE" \
                -c "$COMMENT (update)"; then
            ok "Updated $CSW_PATH"
        else
            fail "Failed to update $CSW_PATH"
        fi
    else
        # Check if already exists — if so, skip rather than fail
        if csw-config-cli exists "$CSW_PATH" 2>/dev/null | grep -q "true"; then
            echo "  [SKIP] Already exists in CSW: $CSW_PATH"
            (( PASS++ )) || true
            echo ""
            continue
        fi
        echo "  Creating $CSW_PATH ..."
        if csw-config-cli create "$CSW_PATH" \
                -i "$LOCAL_FILE" \
                -c "$COMMENT"; then
            ok "Created $CSW_PATH"
        else
            fail "Failed to create $CSW_PATH"
        fi
    fi

    # Set as active (resetActiveVersion sets latest as active)
    echo "  Setting active version for $CSW_PATH ..."
    if csw-config-cli resetActiveVersion "$CSW_PATH" \
            -c "Set initial active version"; then
        ok "Active version set for $CSW_PATH"
    else
        fail "Failed to set active version for $CSW_PATH"
    fi

    (( PASS++ )) || true
    echo ""
done

# ── Summary ───────────────────────────────────────────────────────────────────
echo "============================================================"
echo "  Done: $PASS succeeded, $FAIL failed"
echo "============================================================"
echo ""
echo "To verify, list all files under $CSW_DIR:"
echo "  csw-config-cli list --pattern $CSW_DIR"
