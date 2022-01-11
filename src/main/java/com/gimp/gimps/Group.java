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
package com.gimp.gimps;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import okhttp3.OkHttpClient;

@Slf4j
public class Group
{
	final static int OFFLINE_WORLD = 0;

	@Getter
	final private List<GimPlayer> gimps = new ArrayList<>();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private WorldMapPointManager worldMapPointManager;

	final private HiscoreClient hiscoreClient;

	@Getter
	private boolean loaded = false;

	public Group()
	{
		OkHttpClient okHttpClient = new OkHttpClient();
		hiscoreClient = new HiscoreClient(okHttpClient);
	}

	/**
	 * Loads player data to the Group once the client has finished loading clan
	 * data. Initializes data for the local gimp.
	 */
	public CompletableFuture<Void> load()
	{
		CompletableFuture<Void> loadingResult = new CompletableFuture<>();
		clientThread.invokeLater(() ->
		{
			ClanSettings gimClanSettings = client.getClanSettings(ClanID.GROUP_IRONMAN);
			if (gimClanSettings == null)
			{
				// ClanSettings not loaded yet, retry
				return false;
			}
			List<ClanMember> clanMembers = gimClanSettings.getMembers();
			for (ClanMember member : clanMembers)
			{
				String name = member.getName();
				int world = getCurrentWorld(name);
				gimps.add(new GimPlayer(name, world));
			}
			initLocalGimp();
			loaded = true;
			loadingResult.complete(null);
			return true;
		});
		return loadingResult;
	}

	/**
	 * Maps a raw GimPlayer data object to a GimPlayer in the Group.
	 *
	 * @param gimpData a GimPlayer instance holding broadcast data
	 */
	public void update(GimPlayer gimpData)
	{
		for (GimPlayer gimp : gimps)
		{
			String gimpName = gimpData.getName();
			if (gimp.getName().equals(gimpName))
			{
				if (gimpData.getHp() != null)
				{
					gimp.setHp(gimpData.getHp());
				}
				if (gimpData.getMaxHp() != null)
				{
					gimp.setMaxHp(gimpData.getMaxHp());
				}
				if (gimpData.getPrayer() != null)
				{
					gimp.setPrayer(gimpData.getPrayer());
				}
				if (gimpData.getMaxPrayer() != null)
				{
					gimp.setMaxPrayer(gimpData.getMaxPrayer());
				}
				if (gimpData.getCustomStatus() != null)
				{
					gimp.setCustomStatus(gimpData.getCustomStatus());
				}
				if (gimpData.getLocation() != null)
				{
					setLocation(gimpName, gimpData.getLocation());
				}
			}
		}
	}

	public void unload()
	{
		clearMapPoints();
		gimps.clear();
		loaded = false;
	}

	public GimPlayer getGimp(String name)
	{
		for (GimPlayer gimp : gimps)
		{
			if (gimp.getName().equals(name))
			{
				return gimp;
			}
		}
		return null;
	}

	public GimPlayer getLocalGimp()
	{
		final Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			return getGimp(localPlayer.getName());
		}
		return null;
	}

	/**
	 * Initializes the local GimPlayer if that player exists using data
	 * available on the client.
	 */
	public void initLocalGimp()
	{
		Player localPlayer = client.getLocalPlayer();
		GimPlayer localGimp = getLocalGimp();
		if (localPlayer != null && localGimp != null)
		{
			localGimp.setHp(client.getBoostedSkillLevel(Skill.HITPOINTS));
			localGimp.setMaxHp(client.getRealSkillLevel(Skill.HITPOINTS));
			localGimp.setPrayer(client.getBoostedSkillLevel(Skill.PRAYER));
			localGimp.setMaxPrayer(client.getRealSkillLevel(Skill.PRAYER));
			GimLocation location = new GimLocation(localPlayer.getWorldLocation());
			localGimp.setLocation(location);
			getHiscores(localGimp.getName());
		}
	}

	public List<String> getNames()
	{
		List<String> names = new ArrayList<>();
		for (GimPlayer gimp : gimps)
		{
			names.add(gimp.getName());
		}
		return names;
	}

	private void clearMapPoints()
	{
		for (GimPlayer gimp : gimps)
		{
			GimLocation gimLocation = gimp.getLocation();
			if (gimLocation != null)
			{
				WorldMapPoint lastWorldMapPoint = gimLocation.getWorldMapPoint();
				worldMapPointManager.removeIf(x -> x == lastWorldMapPoint);
			}
		}
	}

	/**
	 * Sets a GimPlayer's location using the provided location data and
	 * updates its icon on the world map.
	 *
	 * @param name     GimPlayer name
	 * @param location GimPlayer location data
	 */
	public void setLocation(String name, GimLocation location)
	{
		GimPlayer gimp = getGimp(name);
		if (gimp == null)
		{
			return;
		}
		// Remove existing world map point
		GimLocation gimLocation = gimp.getLocation();
		if (gimLocation != null)
		{
			WorldMapPoint lastWorldMapPoint = gimLocation.getWorldMapPoint();
			worldMapPointManager.removeIf(x -> x == lastWorldMapPoint);
		}
		// Create new GimLocation from raw data
		GimLocation newGimLocation = new GimLocation(
			location.getX(),
			location.getY(),
			location.getPlane()
		);
		// Set GimPlayer location to new location
		gimp.setLocation(newGimLocation);
		// Add point to world map (if not local player)
		if (gimp != getLocalGimp() && gimp.getWorld() != OFFLINE_WORLD)
		{
			worldMapPointManager.add(newGimLocation.getWorldMapPoint());
		}
	}

	/**
	 * Sets the world number of the GimPlayer by name and removes
	 * the map point if the world number is 0, e.g. the player is offline.
	 *
	 * @param name  GimPlayer name
	 * @param world world number
	 */
	public void setWorld(String name, int world)
	{
		GimPlayer gimp = getGimp(name);
		if (gimp == null)
		{
			return;
		}
		gimp.setWorld(world);
		// If player is offline, remove map point
		if (world == OFFLINE_WORLD)
		{
			GimLocation gimLocation = gimp.getLocation();
			if (gimLocation != null)
			{
				WorldMapPoint lastWorldMapPoint = gimLocation.getWorldMapPoint();
				worldMapPointManager.removeIf(x -> x == lastWorldMapPoint);
			}
		}
	}

	/**
	 * Gets the world of a player by name, returns 0 if the player
	 * is offline.
	 *
	 * @param name GimPlayer name
	 * @return world number
	 */
	public int getCurrentWorld(String name)
	{
		ClanChannel gimClanChannel = client.getClanChannel(ClanID.GROUP_IRONMAN);
		if (validateGimpName(name) && gimClanChannel != null)
		{
			ClanChannelMember onlineMember = gimClanChannel.findMember(name);
			if (onlineMember != null)
			{
				return onlineMember.getWorld();
			}
		}
		return OFFLINE_WORLD;
	}

	public CompletableFuture<HiscoreResult> getHiscores(String name)
	{
		CompletableFuture<HiscoreResult> hiscoreResponse = new CompletableFuture<>();
		GimPlayer gimp = getGimp(name);
		hiscoreClient.lookupAsync(name, HiscoreEndpoint.NORMAL).whenCompleteAsync((result, ex) ->
		{
			if (result == null || ex != null)
			{
				if (ex != null)
				{
					log.warn("Error fetching Hiscore data " + ex.getMessage());
				}
				return;
			}
			// Successful hiscores lookup
			gimp.setHiscores(result);
			hiscoreResponse.complete(result);
		});
		return hiscoreResponse;
	}

	private boolean validateGimpName(String name)
	{
		return getGimp(name) != null;
	}
}