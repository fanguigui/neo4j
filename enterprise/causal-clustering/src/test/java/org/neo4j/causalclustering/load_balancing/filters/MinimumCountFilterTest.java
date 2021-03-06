/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.causalclustering.load_balancing.filters;

import org.junit.Test;

import java.util.Set;

import static java.util.Collections.emptySet;
import static org.junit.Assert.assertEquals;
import static org.neo4j.helpers.collection.Iterators.asSet;

public class MinimumCountFilterTest
{
    @Test
    public void shouldFilterBelowCount() throws Exception
    {
        // given
        MinimumCountFilter<Integer> minFilter = new MinimumCountFilter<>( 3 );

        Set<Integer> input = asSet( 1, 2 );

        // when
        Set<Integer> output = minFilter.apply( input );

        // then
        assertEquals( emptySet(), output );
    }

    @Test
    public void shouldPassAtCount() throws Exception
    {
        // given
        MinimumCountFilter<Integer> minFilter = new MinimumCountFilter<>( 3 );

        Set<Integer> input = asSet( 1, 2, 3 );

        // when
        Set<Integer> output = minFilter.apply( input );

        // then
        assertEquals( input, output );
    }

    @Test
    public void shouldPassAboveCount() throws Exception
    {
        // given
        MinimumCountFilter<Integer> minFilter = new MinimumCountFilter<>( 3 );

        Set<Integer> input = asSet( 1, 2, 3, 4 );

        // when
        Set<Integer> output = minFilter.apply( input );

        // then
        assertEquals( input, output );
    }
}
