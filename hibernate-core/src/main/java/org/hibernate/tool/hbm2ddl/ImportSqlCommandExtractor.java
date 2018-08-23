/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.tool.hbm2ddl;

import java.io.Reader;

import org.hibernate.service.Service;

/**
 * Contract for extracting statements from <i>import.sql</i> script.
 *
 * @author Lukasz Antoniak (lukasz dot antoniak at gmail dot com)
 */
public interface ImportSqlCommandExtractor extends Service {
	/**
	 * @param reader Character stream reader of SQL script.
	 * @return List of single SQL statements. Each command may or may not contain semicolon at the end.
	 */
	public String[] extractCommands(Reader reader);
}
