/*
 * Copyright (c) 2016 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.couchbase.client.java.search.result;

import com.couchbase.client.core.annotation.SinceCouchbase;
import com.couchbase.client.core.annotation.Stability;
import com.couchbase.client.core.api.search.result.CoreSearchRow;
import com.couchbase.client.java.codec.JsonSerializer;
import com.couchbase.client.java.codec.TypeRef;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.search.HighlightStyle;
import com.couchbase.client.java.search.SearchOptions;
import com.couchbase.client.java.search.SearchQuery;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An FTS result row (or hit).
 *
 * @since 2.3.0
 */
public class SearchRow {
    private final CoreSearchRow internal;
    private final JsonSerializer serializer;

    public SearchRow(CoreSearchRow internal, JsonSerializer serializer) {
        this.internal = internal;
        this.serializer = serializer;
    }

    /**
     * The name of the FTS index that gave this result.
     */
    public String index() {
        return internal.index();
    }

    /**
     * The id of the matching document.
     */
    public String id() {
        return internal.id();
    }

    /**
     * The score of this hit.
     */
    public double score() {
        return internal.score();
    }

    /**
     * Returns the keyset of this search row, which contains the keys
     * associated with the result. The keyset is used for pagination
     * or other advanced search operations.
     *
     * @return the keyset of this search row
     * @see SearchOptions#searchBefore(SearchKeyset)
     * @see SearchOptions#searchAfter(SearchKeyset)
     */
    @SinceCouchbase("6.6.1")
    @Stability.Volatile
    public SearchKeyset keyset() { return new SearchKeyset(this.internal.keyset()); }

    /**
     * If {@link SearchOptions#explain(boolean)} was set to true, returns an explanation of the match.
     * Otherwise, returns an empty object. Intended for diagnostic use only;
     * the structure of the JSON is unspecified, and not part of the public committed API.
     */
    public JsonObject explanation() {
        byte[] bytes = internal.explanation();
        return bytes.length == 0 ? JsonObject.create() : JsonObject.fromJson(bytes);
    }

    /**
     * This rows's location, as an {@link SearchRowLocations} map-like object.
     */
    public Optional<SearchRowLocations> locations() {
        return internal.locations().map(SearchRowLocations::new);
    }

    /**
     * The fragments for each field that was requested as highlighted
     * (as defined in the {@link SearchOptions#highlight(HighlightStyle, String...) SearchParams}).
     * <p>
     * A fragment is an extract of the field's value where the matching terms occur.
     * Matching terms are surrounded by a <code>&lt;match&gt;</code> tag.
     *
     * @return the fragments as a {@link Map}. Keys are the fields.
     */
    public Map<String, List<String>> fragments() {
        return internal.fragments();
    }

    /**
     * The value of each requested field (as defined in the {@link SearchQuery}.
     *
     * @return the fields mapped to the given target type
     */
    public <T> T fieldsAs(final Class<T> target) {
        if (internal.fields() == null) {
            return null;
        }
        return serializer.deserialize(target, internal.fields());
    }

    /**
     * The value of each requested field (as defined in the {@link SearchQuery}.
     *
     * @return the fields mapped to the given target type
     */
    public <T> T fieldsAs(final TypeRef<T> target) {
        if (internal.fields() == null) {
            return null;
        }
        return serializer.deserialize(target, internal.fields());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchRow searchRow = (SearchRow) o;
        return Objects.equals(internal, searchRow.internal);
    }

    @Override
    public int hashCode() {
        return internal.hashCode();
    }

    @Override
    public String toString() {
        return internal.toString();
    }
}
