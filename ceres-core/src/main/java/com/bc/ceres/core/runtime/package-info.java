/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

/**
 * Provides the module runtime framework. The main entry point into this API is the
 * {@link RuntimeFactory} class.
 * <p>The only interface in this package which is intended to be implemented
 * by clients is the {@link Activator} interface. Activators are invoked, when a
 * module starts or stops.
 * @since Ceres 0.5
 */
package com.bc.ceres.core.runtime;
