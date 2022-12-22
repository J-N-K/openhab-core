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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.core.thing.profiles.StateProfile;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;

/**
 * The {@link UoMProfileTest} contains tests for the {@link UoMProfile}
 *
 * @author Jan N. Klug - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@NonNullByDefault
public class UoMProfileTest {

    private @Mock @NonNullByDefault({}) ProfileCallback callbackMock;
    private @Mock @NonNullByDefault({}) ProfileContext profileContextMock;
    private final Map<String, Object> configuration = new HashMap<>();

    @BeforeEach
    public void setup() {
        configuration.clear();

        when(profileContextMock.getConfiguration()).thenAnswer(i -> new Configuration(configuration));
    }

    private static Stream<Arguments> argumentProvider() {
        return Stream.of( //
                // unit is added for DecimalType
                Arguments.of("°C", new DecimalType("5"), new QuantityType<>("5 °C")),
                // unit is converted when compatible
                Arguments.of("mm", new QuantityType<>("0.015 m"), new QuantityType<>("15 mm")),
                // original is returned if unit incompatible
                Arguments.of("A", new QuantityType<>("15 V"), new QuantityType<>("15 V")),
                // unit is stripped when not configured
                Arguments.of(null, new QuantityType<>("21 kg"), new DecimalType("21")),
                // DecimalType untouched when unit not configured
                Arguments.of(null, new DecimalType("17"), new DecimalType("17")));
    }

    @ParameterizedTest
    @MethodSource("argumentProvider")
    public void testCommandFromItem(@Nullable String unit, Command command, Command expected) {
        if (unit != null) {
            configuration.put(UoMProfile.UNIT_PARAM, unit);
        }
        StateProfile profile = new UoMProfile(callbackMock, profileContextMock);

        profile.onCommandFromItem(command);

        verify(callbackMock).handleCommand(expected);
    }

    @ParameterizedTest
    @MethodSource("argumentProvider")
    public void testStateFromItem(@Nullable String unit, State state, State expected) {
        if (unit != null) {
            configuration.put(UoMProfile.UNIT_PARAM, unit);
        }
        StateProfile profile = new UoMProfile(callbackMock, profileContextMock);

        profile.onStateUpdateFromItem(state);

        verifyNoInteractions(callbackMock);
    }

    @ParameterizedTest
    @MethodSource("argumentProvider")
    public void testCommandFromHandler(@Nullable String unit, Command command, Command expected) {
        if (unit != null) {
            configuration.put(UoMProfile.UNIT_PARAM, unit);
        }
        StateProfile profile = new UoMProfile(callbackMock, profileContextMock);

        profile.onCommandFromHandler(command);

        verify(callbackMock).sendCommand(expected);
    }

    @ParameterizedTest
    @MethodSource("argumentProvider")
    public void testStateFromHandler(@Nullable String unit, State state, State expected) {
        if (unit != null) {
            configuration.put(UoMProfile.UNIT_PARAM, unit);
        }
        StateProfile profile = new UoMProfile(callbackMock, profileContextMock);

        profile.onStateUpdateFromHandler(state);

        verify(callbackMock).sendUpdate(expected);
    }
}
