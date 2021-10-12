/*
 * Copyright (c) 2021, David Vorona <davidavorona@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gimp;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.api.coords.WorldPoint;
import java.awt.image.BufferedImage;
import java.util.*;

@Slf4j
@PluginDescriptor(
	name = "GIMP"
)
public class GIMPlugin extends Plugin
{
	private static final BufferedImage PLAYER_ICON;

	static
	{
		PLAYER_ICON = new BufferedImage(37, 37, BufferedImage.TYPE_INT_ARGB);
		final BufferedImage playerIcon = ImageUtil.loadImageResource(GIMPlugin.class, "gimpoint.png");
		PLAYER_ICON.getGraphics().drawImage(playerIcon, 0, 0, null);
	}

	private GIMPLocationManager gimpLocationManager;

	private LocationBroadcastManager locationBroadcastManager;

	private Timer timer;

	@Getter(AccessLevel.PACKAGE)
	private WorldMapPoint playerWaypoint;

	@Inject
	WorldMapPointManager worldMapPointManager;

	@Inject
	private Client client;

	@Inject
	private GIMPConfig config;

	@Override
	protected void startUp() throws Exception
	{
		log.info("GIMP started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("GIMP stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN && timer != null)
		{
			timer.cancel();
		}
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged clanChannelChanged)
	{
		Player localPlayer = client.getLocalPlayer();
		ClanChannel changedClanChannel = clanChannelChanged.getClanChannel();
		if (changedClanChannel != null && localPlayer != null)
		{
			ClanChannel gimClanChannel = client.getClanChannel(ClanID.GROUP_IRONMAN);
			if (gimClanChannel != null)
			{
				String changedClanChannelName = changedClanChannel.getName();
				String gimClanChannelName = gimClanChannel.getName();
				// ClanChannelChanged event does not register joining a Group Ironman clan
				// but checking for it here seems to work
				if (gimClanChannelName.equals(changedClanChannelName))
				{
					// Never reaches this code
					log.info("GIM clan joined: " + gimClanChannelName);
				}
				else
				{
					log.info("GIM clan already joined: " + gimClanChannelName);
				}
				List<ClanChannelMember> clanChannelMembers = gimClanChannel.getMembers();
				ArrayList<String> gimpNames = new ArrayList<>();
				for (ClanChannelMember member : clanChannelMembers)
				{
					gimpNames.add(member.getName());
				}
				gimpLocationManager = new GIMPLocationManager(gimpNames);
				startBroadcast();
			}
			else if (timer != null)
			{
				timer.cancel();
			}
		}
	}

	private void startBroadcast()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			locationBroadcastManager = new LocationBroadcastManager();
			timer = new Timer();
			TimerTask locationPingTask = new TimerTask()
			{
				@SneakyThrows
				public void run()
				{
					// only ping if world map is open
					final Widget worldMapView = client.getWidget(WidgetInfo.WORLD_MAP_VIEW);
					if (worldMapView != null)
					{
						Map<String, GIMPLocation> locationData = locationBroadcastManager.ping();
						gimpLocationManager.update(locationData);
						Map<String, WorldPoint> gimpWorldPoints = gimpLocationManager.getGimpWorldPoints();
						for (String name : gimpWorldPoints.keySet())
						{
							WorldPoint worldPoint = gimpWorldPoints.get(name);
							playerWaypoint = new WorldMapPoint(worldPoint, PLAYER_ICON);
							playerWaypoint.setTarget(playerWaypoint.getWorldPoint());
							worldMapPointManager.add(playerWaypoint);
						}
					}
				}
			};
			TimerTask locationBroadcastTask = new TimerTask()
			{
				@SneakyThrows
				public void run()
				{
					WorldPoint worldPoint = localPlayer.getWorldLocation();
					GIMPLocation location = new GIMPLocation(
						worldPoint.getX(),
						worldPoint.getY(),
						worldPoint.getPlane()
					);
					locationBroadcastManager.broadcast(localPlayer.getName(), location);
				}
			};
			timer.schedule(locationPingTask, 0, 5000);
			timer.schedule(locationBroadcastTask, 2500, 5000);
		}
	}

	@Provides
	GIMPConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GIMPConfig.class);
	}
}