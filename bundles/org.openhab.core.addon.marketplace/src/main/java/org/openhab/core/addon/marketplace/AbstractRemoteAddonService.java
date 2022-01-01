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
package org.openhab.core.addon.marketplace;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.addon.Addon;
import org.openhab.core.addon.AddonEventFactory;
import org.openhab.core.addon.AddonService;
import org.openhab.core.addon.AddonType;
import org.openhab.core.events.Event;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.storage.Storage;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Reference;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The {@link AbstractRemoteAddonService} implements basic functionality of a remote add-on-service
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public abstract class AbstractRemoteAddonService implements AddonService {
    protected static final Map<String, AddonType> TAG_ADDON_TYPE_MAP = Map.of( //
            "automation", new AddonType("automation", "Automation"), //
            "binding", new AddonType("binding", "Bindings"), //
            "misc", new AddonType("misc", "Misc"), //
            "persistence", new AddonType("persistence", "Persistence"), //
            "transformation", new AddonType("transformation", "Transformations"), //
            "ui", new AddonType("ui", "User Interfaces"), //
            "voice", new AddonType("voice", "Voice"));

    protected final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create();
    protected final Set<MarketplaceAddonHandler> addonHandlers = new HashSet<>();
    protected @NonNullByDefault({}) Storage<String> installedAddonStorage;
    protected final EventPublisher eventPublisher;
    protected final ConfigurationAdmin configurationAdmin;
    protected List<Addon> cachedAddons = List.of();

    public AbstractRemoteAddonService(@Reference EventPublisher eventPublisher,
            @Reference ConfigurationAdmin configurationAdmin) {
        this.eventPublisher = eventPublisher;
        this.configurationAdmin = configurationAdmin;
    }

    @Override
    public void refreshSource() {
        List<Addon> addons = new ArrayList<>();
        installedAddonStorage.stream().map(e -> Objects.requireNonNull(gson.fromJson(e.getValue(), Addon.class)))
                .forEach(addons::add);
        addons.forEach(a -> a.setInstalled(true));

        // create lookup list to make sure installed addons take precedence
        List<String> installedAddons = addons.stream().map(Addon::getId).collect(Collectors.toList());

        if (remoteEnabled()) {
            addons.addAll(getRemoteAddons(installedAddons));
        }

        cachedAddons = addons;
    }

    /**
     * get all addons from remote
     *
     * @param installedAddons list of addon ids that are already installed locally (used for filtering)
     * @return a list of {@link Addon} that are available on the remote side
     */
    protected abstract List<Addon> getRemoteAddons(List<String> installedAddons);

    @Override
    public List<Addon> getAddons(@Nullable Locale locale) {
        // TODO: remove this refresh once a better solution is found
        refreshSource();
        return cachedAddons;
    }

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

    /**
     * check if remote services are enabled
     *
     * @return true if network access is allowed
     */
    protected boolean remoteEnabled() {
        try {
            Configuration configuration = configurationAdmin.getConfiguration("org.openhab.addons", null);
            return (boolean) Objects.requireNonNullElse(configuration.getProperties().get("remote"), true);
        } catch (IOException e) {
            return true;
        }
    }

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
}
