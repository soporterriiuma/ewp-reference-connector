# IIA Hash Calculation

This document describes how this connector calculates the hash used for IIAs (`iia-hash` / `conditionsHash`), with special focus on ordering.

## Context

This document is intended to be usable without access to the source code.

The IIA hash is calculated from a deterministic plain-text representation of selected IIA fields. The connector first converts the IIA into the EWP IIAs API XML shape, normalizes the order of several lists, extracts a `text-to-hash` string from that XML, and finally applies SHA-256 to that string.

The implementation this document is based on uses:

- a Java converter that builds the IIA API object and normalizes list ordering
- an XSL transformation that extracts the `text-to-hash` value
- SHA-256 over the UTF-8 bytes of `text-to-hash`

Repository source paths, if available:

- Main hash method: `ewp-reference-connector/src/main/java/eu/erasmuswithoutpaper/iia/control/HashCalculationUtility.java`
- Hash transformation: `ewp-reference-connector/src/main/resources/META-INF/transform_version_7.xsl`
- IIA object conversion and ordering normalization: `ewp-reference-connector/src/main/java/eu/erasmuswithoutpaper/iia/control/IiaConverter.java`

## High-Level Algorithm

1. Build an IIA API XML object from the IIA data.
2. Normalize ordering inside that object.
3. Wrap the single IIA inside an IIAs response object.
4. Marshal that response object to XML.
5. Transform the XML into a simplified structure containing one `<text-to-hash>` value.
6. Read the generated `<text-to-hash>` value.
7. Calculate SHA-256 over the UTF-8 bytes of that exact `text-to-hash` string.
8. Return the digest as lowercase hexadecimal.

Important: the active hash is not calculated over the full XML document. It is calculated over the plain text produced by the transformation.

## Text-To-Hash Format

The transformation builds one `<text-to-hash>` string per `<iia>`.

The string is a concatenation of field markers:

- Element values use: `_name=value_`
- Attribute values use: `_@name=value@_`
- `terminated-as-a-whole` uses: `_@terminated-as-a-whole@_`

Example shape:

```text
_@terminated-as-a-whole@__iia-id_1=LOCAL-ID__iia-id_2=PARTNER-ID_...
```

## Top-Level Ordering

The transformation processes each `<iia>` in XML document order:

```xsl
//*[local-name()='iia']
```

In the active code path, the hash method wraps and hashes one IIA at a time, so this normally means there is only one `<iia>` in the transformed input.

## Partner Ordering

Partner IDs are included before cooperation-condition data.

The transformation iterates partners in their XML order:

```xsl
*[local-name()='partner']
```

Each partner contributes:

```text
_iia-id_1=..._
_iia-id_2=..._
```

The suffix number is the 1-based partner position in the XML.

The partner XML order is created as follows:

1. Partners are collected from all cooperation conditions into a `HashMap<String, IiaPartner>`, keyed by institution ID.
2. The map values are added to the API object.
3. The resulting partner list is sorted so that the partner whose `hei-id` equals the requested/local `hei_id` comes first.

Ordering consequence:

- The local/requested HEI partner is forced to position 1.
- The relative order of any remaining partners depends on `HashMap` iteration order unless there are only two partners.
- Since the partner position is embedded in names like `iia-id_1` and `iia-id_2`, changing partner order changes the hash.

## Cooperation-Condition Group Ordering

The connector groups cooperation-condition records by mobility type:

- `Staff-Teaching` goes to `staff-teacher-mobility-spec`
- `Staff-Training` goes to `staff-training-mobility-spec`
- `Student-Studies` goes to `student-studies-mobility-spec`
- `Student-Training` goes to `student-traineeship-mobility-spec`

The transformation then processes the actual XML children of `<cooperation-conditions>` in XML document order:

```xsl
*[local-name()='cooperation-conditions']/*
```

So the group order used by the hash is the order emitted by JAXB for the `cooperation-conditions` XML, after the converter has populated and normalized the lists.

## Mobility-Spec Ordering

Inside each mobility-spec group, the connector sorts the list before hashing.

For `student-studies-mobility-spec` and `student-traineeship-mobility-spec`, the sort key is this concatenated string:

```text
sendingHeiId + receivingHeiId + sendingOunitId + receivingOunitId + allEqfLevelsInCurrentOrder
```

For `staff-teacher-mobility-spec` and `staff-training-mobility-spec`, the sort key is this concatenated string:

```text
sendingHeiId + receivingHeiId + sendingOunitId + receivingOunitId
```

Null fields are skipped. Comparison is normal Java `String.compareTo(...)`, so ordering is lexicographic and case-sensitive.

## Ordering Inside Each Mobility Spec

Before sorting the mobility specs themselves, the connector also sorts several nested lists inside every spec.

These rules are applied to all four mobility spec types:

- `receiving-contact` is sorted by:

```text
firstContactNameValue + firstEmail
```

- `sending-contact` is sorted by:

```text
firstContactNameValue + firstEmail
```

- `recommended-language-skill` is sorted by:

```text
language + cefrLevel
```

- `subject-area` is sorted by:

```text
iscedFCodeValue + iscedClarification
```

Null fields are skipped. Comparison is Java `String.compareTo(...)`.

Contact data is sorted, but it is not included in the final hash text because the transformation excludes descendants of `sending-contact` and `receiving-contact`.

## Academic-Year Ordering

Before a mobility spec is created, the list of receiving academic-year IDs is sorted with natural string order:

```java
cc.getReceivingAcademicYearId().sort(Comparator.naturalOrder());
```

Then:

- the first sorted value becomes `receiving-first-academic-year-id`
- the second sorted value, if present, becomes `receiving-last-academic-year-id`

The transformation excludes these two fields during the recursive descendant pass, then appends them at the end of each mobility spec in this fixed order:

1. `receiving-first-academic-year-id`
2. `receiving-last-academic-year-id`

Therefore these academic-year values are always hashed last within each mobility spec, regardless of where they appear in the XML.

## Element And Attribute Ordering During Traversal

For each mobility spec, the transformation walks all descendant elements in XML document order:

```xsl
.//*
```

For every included descendant element:

1. Attributes are dumped first.
2. The element value is dumped only if the element has no child elements.

The generated field name for an attribute is:

```text
grandparent.parent.element.attribute
```

The generated field name for a leaf element is:

```text
grandparent.parent.element
```

Ordering consequence:

- Child element order comes from the marshalled XML.
- Attribute order comes from the attribute order exposed while traversing the marshalled XML.
- Leaf element values are included after that leaf's attributes.

## Fields Excluded From The Hash

The transformation excludes:

- all descendants of `sending-contact`
- all descendants of `receiving-contact`
- `receiving-first-academic-year-id` during the recursive pass
- `receiving-last-academic-year-id` during the recursive pass
- any element that has `not-yet-defined="true"` or `not-yet-defined="1"` on itself or any ancestor
- attributes named `not-yet-defined`
- attributes named `v6-value`

The academic-year fields are not fully excluded; they are appended separately at the end of each mobility spec.

## Special ISCED Handling

For `isced-f-code`, if the element has a non-empty `v6-value` attribute, the hash uses the `v6-value` instead of the visible element value.

Any element with `v6-value` also causes the transformed output to include:

```xml
<valid-for-approval>false</valid-for-approval>
```

This flag is not part of `text-to-hash`; it is metadata produced by the transform.

## Terminated-As-A-Whole Handling

If `cooperation-conditions/@terminated-as-a-whole` is `true` or `1`, the text starts with:

```text
_@terminated-as-a-whole@_
```

If the attribute is missing, `false`, or any other value, nothing is added for it.

## Standalone Reproduction Checklist

To reproduce the same IIA hash:

1. Put the local/requested HEI partner first.
2. Keep partner position stable because `iia-id_1`, `iia-id_2`, etc. are part of the text.
3. Sort receiving academic years lexicographically before assigning first/last.
4. Sort each mobility-spec list with the converter's concatenated sort keys.
5. Sort nested contacts, language skills, and subject areas with the converter's concatenated sort keys.
6. Traverse the final XML in document order exactly as described above.
7. Exclude contacts and `not-yet-defined` values from the hash text.
8. Append receiving first academic year and receiving last academic year at the end of each mobility spec.
9. Hash the final `text-to-hash` string as UTF-8 with SHA-256.
