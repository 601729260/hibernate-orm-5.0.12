/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.envers.configuration.internal.metadata;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.MappingException;
import org.hibernate.envers.configuration.internal.AuditEntitiesConfiguration;
import org.hibernate.envers.configuration.internal.GlobalConfiguration;
import org.hibernate.envers.internal.entities.mapper.relation.MiddleComponentData;
import org.hibernate.envers.internal.entities.mapper.relation.MiddleIdData;
import org.hibernate.envers.internal.entities.mapper.relation.query.OneEntityQueryGenerator;
import org.hibernate.envers.internal.entities.mapper.relation.query.RelationQueryGenerator;
import org.hibernate.envers.internal.entities.mapper.relation.query.ThreeEntityQueryGenerator;
import org.hibernate.envers.internal.entities.mapper.relation.query.TwoEntityOneAuditedQueryGenerator;
import org.hibernate.envers.internal.entities.mapper.relation.query.TwoEntityQueryGenerator;
import org.hibernate.envers.strategy.AuditStrategy;

/**
 * Builds query generators, for reading collection middle tables, along with any related entities.
 * The related entities information can be added gradually, and when complete, the query generator can be built.
 *
 * @author Adam Warski (adam at warski dot org)
 */
public final class QueryGeneratorBuilder {
	private final GlobalConfiguration globalCfg;
	private final AuditEntitiesConfiguration verEntCfg;
	private final AuditStrategy auditStrategy;
	private final MiddleIdData referencingIdData;
	private final String auditMiddleEntityName;
	private final List<MiddleIdData> idDatas;
	private final boolean revisionTypeInId;

	QueryGeneratorBuilder(
			GlobalConfiguration globalCfg,
			AuditEntitiesConfiguration verEntCfg,
			AuditStrategy auditStrategy,
			MiddleIdData referencingIdData,
			String auditMiddleEntityName,
			boolean revisionTypeInId) {
		this.globalCfg = globalCfg;
		this.verEntCfg = verEntCfg;
		this.auditStrategy = auditStrategy;
		this.referencingIdData = referencingIdData;
		this.auditMiddleEntityName = auditMiddleEntityName;
		this.revisionTypeInId = revisionTypeInId;

		idDatas = new ArrayList<MiddleIdData>();
	}

	void addRelation(MiddleIdData idData) {
		idDatas.add( idData );
	}

	RelationQueryGenerator build(MiddleComponentData... componentDatas) {
		if ( idDatas.size() == 0 ) {
			return new OneEntityQueryGenerator(
					verEntCfg, auditStrategy, auditMiddleEntityName, referencingIdData,
					revisionTypeInId, componentDatas
			);
		}
		else if ( idDatas.size() == 1 ) {
			if ( idDatas.get( 0 ).isAudited() ) {
				return new TwoEntityQueryGenerator(
						globalCfg, verEntCfg, auditStrategy, auditMiddleEntityName, referencingIdData,
						idDatas.get( 0 ), revisionTypeInId, componentDatas
				);
			}
			else {
				return new TwoEntityOneAuditedQueryGenerator(
						verEntCfg, auditStrategy, auditMiddleEntityName, referencingIdData,
						idDatas.get( 0 ), revisionTypeInId, componentDatas
				);
			}
		}
		else if ( idDatas.size() == 2 ) {
			// All entities must be audited.
			if ( !idDatas.get( 0 ).isAudited() || !idDatas.get( 1 ).isAudited() ) {
				throw new MappingException(
						"Ternary relations using @Audited(targetAuditMode = NOT_AUDITED) are not supported."
				);
			}

			return new ThreeEntityQueryGenerator(
					globalCfg, verEntCfg, auditStrategy, auditMiddleEntityName, referencingIdData,
					idDatas.get( 0 ), idDatas.get( 1 ), revisionTypeInId, componentDatas
			);
		}
		else {
			throw new IllegalStateException( "Illegal number of related entities." );
		}
	}

	/**
	 * @return Current index of data in the array, which will be the element of a list, returned when executing a query
	 *         generated by the built query generator.
	 */
	int getCurrentIndex() {
		return idDatas.size();
	}
}
