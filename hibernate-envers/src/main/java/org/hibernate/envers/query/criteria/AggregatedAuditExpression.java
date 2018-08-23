/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.envers.query.criteria;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.envers.boot.internal.EnversService;
import org.hibernate.envers.internal.reader.AuditReaderImplementor;
import org.hibernate.envers.internal.tools.query.Parameters;
import org.hibernate.envers.internal.tools.query.QueryBuilder;
import org.hibernate.envers.query.criteria.internal.CriteriaTools;
import org.hibernate.envers.query.internal.property.PropertyNameGetter;

/**
 * @author Adam Warski (adam at warski dot org)
 * @author Lukasz Antoniak (lukasz dot antoniak at gmail dot com)
 */
public class AggregatedAuditExpression implements AuditCriterion, ExtendableCriterion {
	private PropertyNameGetter propertyNameGetter;
	private AggregatedMode mode;
	// Correlate subquery with outer query by entity id.
	private boolean correlate;
	private List<AuditCriterion> criterions;

	public AggregatedAuditExpression(PropertyNameGetter propertyNameGetter, AggregatedMode mode) {
		this.propertyNameGetter = propertyNameGetter;
		this.mode = mode;
		criterions = new ArrayList<AuditCriterion>();
	}

	public static enum AggregatedMode {
		MAX,
		MIN
	}

	@Override
	public AggregatedAuditExpression add(AuditCriterion criterion) {
		criterions.add( criterion );
		return this;
	}

	@Override
	public void addToQuery(
			EnversService enversService,
			AuditReaderImplementor versionsReader,
			String entityName,
			QueryBuilder qb,
			Parameters parameters) {
		String propertyName = CriteriaTools.determinePropertyName(
				enversService,
				versionsReader,
				entityName,
				propertyNameGetter
		);

		CriteriaTools.checkPropertyNotARelation( enversService, entityName, propertyName );

		// Make sure our conditions are ANDed together even if the parent Parameters have a different connective
		Parameters subParams = parameters.addSubParameters( Parameters.AND );
		// This will be the aggregated query, containing all the specified conditions
		QueryBuilder subQb = qb.newSubQueryBuilder();

		// Adding all specified conditions both to the main query, as well as to the
		// aggregated one.
		for ( AuditCriterion versionsCriteria : criterions ) {
			versionsCriteria.addToQuery( enversService, versionsReader, entityName, qb, subParams );
			versionsCriteria.addToQuery( enversService, versionsReader, entityName, subQb, subQb.getRootParameters() );
		}

		// Setting the desired projection of the aggregated query
		switch ( mode ) {
			case MIN:
				subQb.addProjection( "min", propertyName, false );
				break;
			case MAX:
				subQb.addProjection( "max", propertyName, false );
		}

		// Correlating subquery with the outer query by entity id. See JIRA HHH-7827.
		if ( correlate ) {
			final String originalIdPropertyName = enversService.getAuditEntitiesConfiguration().getOriginalIdPropName();
			enversService.getEntitiesConfigurations().get( entityName ).getIdMapper().addIdsEqualToQuery(
					subQb.getRootParameters(),
					subQb.getRootAlias() + "." + originalIdPropertyName,
					qb.getRootAlias() + "." + originalIdPropertyName
			);
		}

		// Adding the constrain on the result of the aggregated criteria
		subParams.addWhere( propertyName, "=", subQb );
	}

	/**
	 * Compute aggregated expression in the context of each entity instance separately. Useful for retrieving latest
	 * revisions of all entities of a particular type.<br/>
	 * Implementation note: Correlates subquery with the outer query by entity id.
	 *
	 * @return this (for method chaining).
	 */
	public AggregatedAuditExpression computeAggregationInInstanceContext() {
		correlate = true;
		return this;
	}
}
