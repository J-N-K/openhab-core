/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.thing.internal.profiles;

import java.util.Objects;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.ConfigParser;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.core.thing.profiles.ProfileTypeUID;
import org.openhab.core.thing.profiles.StateProfile;
import org.openhab.core.thing.profiles.SystemProfiles;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.Type;
import org.openhab.core.types.util.UnitUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UoMProfile} allows to convert between {@link DecimalType} and {@link QuantityType}
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public class UoMProfile implements StateProfile {

    static final String UNIT_PARAM = "unit";

    private final Logger logger = LoggerFactory.getLogger(UoMProfile.class);

    private final ProfileCallback callback;

    private final @Nullable Unit<?> unit;
    private boolean disabled = false;

    public UoMProfile(ProfileCallback callback, ProfileContext context) {
        this.callback = callback;

        String unitString = ConfigParser.valueAs(context.getConfiguration().get(UNIT_PARAM), String.class);
        logger.debug("Configuring profile with unit '{}'", unitString);

        if (unitString != null && !unitString.isBlank()) {
            unit = UnitUtils.parseUnit(unitString);
            if (unit == null) {
                logger.error("Unit '{}' can't be parsed, profile is disabled", unitString);
                disabled = true;
            }
        } else {
            unit = null;
        }
    }

    @Override
    public ProfileTypeUID getProfileTypeUID() {
        return SystemProfiles.UOM;
    }

    @Override
    public void onStateUpdateFromItem(State state) {
    }

    @Override
    public void onCommandFromItem(Command command) {
        callback.handleCommand(apply(command));
    }

    @Override
    public void onCommandFromHandler(Command command) {
        callback.sendCommand(apply(command));
    }

    @Override
    public void onStateUpdateFromHandler(State state) {
        callback.sendUpdate(apply(state));
    }

    @SuppressWarnings("unchecked")
    private <T extends Type> T apply(T originalValue) {
        if (!disabled && originalValue instanceof DecimalType decimalType && unit != null) {
            return (T) new QuantityType<>(decimalType.toBigDecimal(), Objects.requireNonNull(unit));
        } else if (!disabled && originalValue instanceof QuantityType<?> quantityType && unit == null) {
            return (T) new DecimalType(quantityType.toBigDecimal());
        } else if (!disabled && originalValue instanceof QuantityType<?> quantityType) {
            QuantityType<?> converted = quantityType.toUnit(Objects.requireNonNull(unit));
            if (converted != null) {
                return (T) converted;
            } else {
                logger.warn("Could not convert '{}' to unit '{}', returning original value.", quantityType, unit);
                return originalValue;
            }
        }
        return originalValue;
    }
}
