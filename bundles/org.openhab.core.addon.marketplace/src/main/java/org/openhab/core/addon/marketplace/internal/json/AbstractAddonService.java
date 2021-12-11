/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.core.addon.marketplace.internal.json;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.addon.Addon;
import org.openhab.core.addon.AddonEventFactory;
import org.openhab.core.addon.AddonService;
import org.openhab.core.addon.AddonType;
import org.openhab.core.addon.marketplace.MarketplaceAddonHandler;
import org.openhab.core.addon.marketplace.MarketplaceHandlerException;
import org.openhab.core.events.Event;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.storage.Storage;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Reference;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The {@link AbstractAddonService} is a
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public abstract class AbstractAddonService implements AddonService {
    protected final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create();
    protected final Set<MarketplaceAddonHandler> addonHandlers = new HashSet<>();
    protected @NonNullByDefault({}) Storage<String> installedAddonStorage;
    protected final EventPublisher eventPublisher;
    protected final ConfigurationAdmin configurationAdmin;

    public AbstractAddonService(@Reference EventPublisher eventPublisher,
            @Reference ConfigurationAdmin configurationAdmin) {
        this.eventPublisher = eventPublisher;
        this.configurationAdmin = configurationAdmin;
    }

    @Override
    public abstract List<Addon> getAddons(@Nullable Locale locale);

    @Override
    public abstract @Nullable Addon getAddon(String id, @Nullable Locale locale);

    @Override
    public abstract List<AddonType> getTypes(@Nullable Locale locale);

    @Override
    public void install(String id) {
        Addon addon = getAddon(id, null);
        if (addon != null) {
            for (MarketplaceAddonHandler handler : addonHandlers) {
                if (handler.supports(addon.getType(), addon.getContentType())) {
                    if (!handler.isInstalled(addon.getId())) {
                        try {
                            handler.install(addon);
                            installedAddonStorage.put(id, gson.toJson(addon));
                            postInstalledEvent(addon.getId());
                        } catch (MarketplaceHandlerException e) {
                            postFailureEvent(addon.getId(), e.getMessage());
                        }
                    } else {
                        postFailureEvent(addon.getId(), "Add-on is already installed.");
                    }
                    return;
                }
            }
        }
        postFailureEvent(id, "Add-on not known.");
    }

    @Override
    public void uninstall(String id) {
        Addon addon = getAddon(id, null);
        if (addon != null) {
            for (MarketplaceAddonHandler handler : addonHandlers) {
                if (handler.supports(addon.getType(), addon.getContentType())) {
                    if (handler.isInstalled(addon.getId())) {
                        try {
                            handler.uninstall(addon);
                            installedAddonStorage.remove(id);
                            postUninstalledEvent(addon.getId());
                        } catch (MarketplaceHandlerException e) {
                            postFailureEvent(addon.getId(), e.getMessage());
                        }
                    } else {
                        installedAddonStorage.remove(id);
                        postFailureEvent(addon.getId(), "Add-on is not installed.");
                    }
                    return;
                }
            }
        }
        postFailureEvent(id, "Add-on not known.");
    }

    @Override
    public abstract @Nullable String getAddonId(URI addonURI);

    private void postInstalledEvent(String extensionId) {
        Event event = AddonEventFactory.createAddonInstalledEvent(extensionId);
        eventPublisher.post(event);
    }

    private void postUninstalledEvent(String extensionId) {
        Event event = AddonEventFactory.createAddonUninstalledEvent(extensionId);
        eventPublisher.post(event);
    }

    private void postFailureEvent(String extensionId, @Nullable String msg) {
        Event event = AddonEventFactory.createAddonFailureEvent(extensionId, msg);
        eventPublisher.post(event);
    }

    protected boolean remoteEnabled() {
        try {
            Configuration configuration = configurationAdmin.getConfiguration("org.openhab.addons", null);
            return (boolean) Objects.requireNonNullElse(configuration.getProperties().get("remote"), true);
        } catch (IOException e) {
            return true;
        }
    }
}
