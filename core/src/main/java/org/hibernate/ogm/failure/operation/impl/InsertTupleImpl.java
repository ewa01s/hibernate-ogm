/*
 * Hibernate OGM, Domain model persistence for NoSQL datastores
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.ogm.failure.operation.impl;

import org.hibernate.ogm.failure.operation.GridDialectOperation;
import org.hibernate.ogm.failure.operation.OperationType;
import org.hibernate.ogm.failure.operation.InsertTuple;
import org.hibernate.ogm.model.key.spi.EntityKeyMetadata;
import org.hibernate.ogm.model.spi.Tuple;

/**
 * @author Gunnar Morling
 */
public class InsertTupleImpl implements InsertTuple {

	private final EntityKeyMetadata entityKeyMetadata;
	private final Tuple tuple;

	public InsertTupleImpl(EntityKeyMetadata entityKeyMetadata, Tuple tuple) {
		this.entityKeyMetadata = entityKeyMetadata;
		this.tuple = tuple;
	}

	@Override
	public EntityKeyMetadata getEntityKeyMetadata() {
		return entityKeyMetadata;
	}

	@Override
	public Tuple getTuple() {
		return tuple;
	}

	@Override
	public <T extends GridDialectOperation> T as(Class<T> type) {
		if ( InsertTuple.class.isAssignableFrom( type ) ) {
			return type.cast( this );
		}

		throw new IllegalArgumentException( "Unexpected type: " + type );
	}

	@Override
	public OperationType getType() {
		return OperationType.INSERT_TUPLE;
	}

	@Override
	public String toString() {
		return "InsertTupleImpl [entityKeyMetadata=" + entityKeyMetadata + ", tuple=" + tuple + "]";
	}
}
