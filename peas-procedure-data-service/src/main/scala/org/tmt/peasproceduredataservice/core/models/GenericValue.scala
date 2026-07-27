package org.tmt.peasproceduredataservice.core.models

// Represents an encoded parameter value as defined in the OpenAPI spec.
// encodedStringValue holds the data as a string:
//   - scalar:   "36.5"
//   - 1D array: "1.0, 2.0, 3.0"
//   - 2D array: "1.0, 2.0; 3.0, 4.0"  (rows separated by "; ")
// dim1 is the fast-changing index (column count for 2D, length for 1D, 1 for scalar)
// dim2 is the row count for 2D, 1 for 1D and scalar
case class GenericValue(
    `type`: String,
    dim1: Int,
    dim2: Int,
    encodedStringValue: String
)
