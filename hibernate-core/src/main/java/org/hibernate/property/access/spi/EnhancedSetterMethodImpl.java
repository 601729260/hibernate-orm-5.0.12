/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.property.access.spi;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.hibernate.PropertyAccessException;
import org.hibernate.PropertySetterAccessException;
import org.hibernate.engine.spi.SelfDirtinessTracker;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.CoreMessageLogger;

import static org.hibernate.internal.CoreLogging.messageLogger;

/**
 * @author Steve Ebersole
 *
 * @deprecated No longer used; {@link EnhancedSetterImpl} should be used instead.
 */
@Deprecated
public class EnhancedSetterMethodImpl implements Setter {
	private static final CoreMessageLogger LOG = messageLogger( EnhancedSetterMethodImpl.class );

	private final Class containerClass;
	private final String propertyName;
	private final Method setterMethod;

	private final boolean isPrimitive;

	public EnhancedSetterMethodImpl(Class containerClass, String propertyName, Method setterMethod) {
		this.containerClass = containerClass;
		this.propertyName = propertyName;
		this.setterMethod = setterMethod;
		this.isPrimitive = setterMethod.getParameterTypes()[0].isPrimitive();
	}

	@Override
	public void set(Object target, Object value, SessionFactoryImplementor factory) {
		try {

			// for enhanced attribute, don't flag as dirty
			if ( target instanceof SelfDirtinessTracker ) {
				( (SelfDirtinessTracker) target ).$$_hibernate_suspendDirtyTracking( true );
				try {
					setterMethod.invoke( target, value );
				}
				finally {
					( (SelfDirtinessTracker) target ).$$_hibernate_suspendDirtyTracking( false );
				}
			}
			else {
				setterMethod.invoke( target, value );
			}

		}
		catch (NullPointerException npe) {
			if ( value == null && isPrimitive ) {
				throw new PropertyAccessException(
						npe,
						"Null value was assigned to a property of primitive type",
						true,
						containerClass,
						propertyName
				);
			}
			else {
				throw new PropertyAccessException(
						npe,
						"NullPointerException occurred while calling",
						true,
						containerClass,
						propertyName
				);
			}
		}
		catch (InvocationTargetException ite) {
			throw new PropertyAccessException(
					ite,
					"Exception occurred inside",
					true,
					containerClass,
					propertyName
			);
		}
		catch (IllegalAccessException iae) {
			throw new PropertyAccessException(
					iae,
					"IllegalAccessException occurred while calling",
					true,
					containerClass,
					propertyName
			);
			//cannot occur
		}
		catch (IllegalArgumentException iae) {
			if ( value == null && isPrimitive ) {
				throw new PropertyAccessException(
						iae,
						"Null value was assigned to a property of primitive type",
						true,
						containerClass,
						propertyName
				);
			}
			else {
				final Class expectedType = setterMethod.getParameterTypes()[0];
				LOG.illegalPropertySetterArgument( containerClass.getName(), propertyName );
				LOG.expectedType( expectedType.getName(), value == null ? null : value.getClass().getName() );
				throw new PropertySetterAccessException(
						iae,
						containerClass,
						propertyName,
						expectedType,
						target,
						value
				);
			}
		}
	}

	@Override
	public String getMethodName() {
		return setterMethod.getName();
	}

	@Override
	public Method getMethod() {
		return setterMethod;
	}

	private Object writeReplace() throws ObjectStreamException {
		return new SerialForm( containerClass, propertyName, setterMethod );
	}

	private static class SerialForm implements Serializable {
		private final Class containerClass;
		private final String propertyName;

		private final Class declaringClass;
		private final String methodName;
		private final Class argumentType;

		private SerialForm(Class containerClass, String propertyName, Method method) {
			this.containerClass = containerClass;
			this.propertyName = propertyName;
			this.declaringClass = method.getDeclaringClass();
			this.methodName = method.getName();
			this.argumentType = method.getParameterTypes()[0];
		}

		private Object readResolve() {
			return new EnhancedSetterMethodImpl( containerClass, propertyName, resolveMethod() );
		}

		@SuppressWarnings("unchecked")
		private Method resolveMethod() {
			try {
				return declaringClass.getDeclaredMethod( methodName, argumentType );
			}
			catch (NoSuchMethodException e) {
				throw new PropertyAccessSerializationException(
						"Unable to resolve setter method on deserialization : " + declaringClass.getName() + "#"
								+ methodName + "(" + argumentType.getName() + ")"
				);
			}
		}
	}
}
