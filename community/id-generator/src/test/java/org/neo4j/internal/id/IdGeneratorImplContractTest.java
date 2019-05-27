/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.internal.id;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;

import org.neo4j.io.fs.EphemeralFileSystemAbstraction;
import org.neo4j.test.extension.EphemeralFileSystemExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.TestDirectoryExtension;
import org.neo4j.test.rule.TestDirectory;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith( {EphemeralFileSystemExtension.class, TestDirectoryExtension.class} )
public class IdGeneratorImplContractTest extends IdGeneratorContractTest
{
    @Inject
    private EphemeralFileSystemAbstraction fs;
    @Inject
    private TestDirectory testDirectory;

    @Override
    protected IdGenerator createIdGenerator( int grabSize )
    {
        IdGeneratorImpl.createGenerator( fs, idGeneratorFile(), 0, false );
        return openIdGenerator( grabSize );
    }

    @Override
    protected IdGenerator openIdGenerator( int grabSize )
    {
        return new IdGeneratorImpl( fs, idGeneratorFile(), grabSize, 1000, false, IdType.NODE, () -> 0L );
    }

    @AfterEach
    void verifyFileCleanup()
    {
        File file = idGeneratorFile();
        if ( file.exists() )
        {
            assertTrue( file.delete() );
        }
    }

    private File idGeneratorFile()
    {
        return testDirectory.file( "testIdGenerator.id" );
    }
}