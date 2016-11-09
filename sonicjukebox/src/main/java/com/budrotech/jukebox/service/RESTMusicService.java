/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package com.budrotech.jukebox.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;

import com.budrotech.jukebox.R;
import com.budrotech.jukebox.domain.Artist;
import com.budrotech.jukebox.domain.Bookmark;
import com.budrotech.jukebox.domain.ChatMessage;
import com.budrotech.jukebox.domain.Genre;
import com.budrotech.jukebox.domain.Indexes;
import com.budrotech.jukebox.domain.JukeboxStatus;
import com.budrotech.jukebox.domain.Lyrics;
import com.budrotech.jukebox.domain.MusicDirectory;
import com.budrotech.jukebox.domain.MusicFolder;
import com.budrotech.jukebox.domain.Playlist;
import com.budrotech.jukebox.domain.SearchCriteria;
import com.budrotech.jukebox.domain.SearchResult;
import com.budrotech.jukebox.domain.ServerInfo;
import com.budrotech.jukebox.domain.Share;
import com.budrotech.jukebox.domain.UserInfo;
import com.budrotech.jukebox.domain.Version;
import com.budrotech.jukebox.service.parser.AlbumListParser;
import com.budrotech.jukebox.service.parser.BookmarkParser;
import com.budrotech.jukebox.service.parser.ChatMessageParser;
import com.budrotech.jukebox.service.parser.ErrorParser;
import com.budrotech.jukebox.service.parser.GenreParser;
import com.budrotech.jukebox.service.parser.IndexesParser;
import com.budrotech.jukebox.service.parser.JukeboxStatusParser;
import com.budrotech.jukebox.service.parser.LicenseParser;
import com.budrotech.jukebox.service.parser.LyricsParser;
import com.budrotech.jukebox.service.parser.MusicDirectoryParser;
import com.budrotech.jukebox.service.parser.MusicFoldersParser;
import com.budrotech.jukebox.service.parser.PlaylistParser;
import com.budrotech.jukebox.service.parser.PlaylistsParser;
import com.budrotech.jukebox.service.parser.RandomSongsParser;
import com.budrotech.jukebox.service.parser.SearchResult2Parser;
import com.budrotech.jukebox.service.parser.SearchResultParser;
import com.budrotech.jukebox.service.parser.ShareParser;
import com.budrotech.jukebox.service.parser.UserInfoParser;
import com.budrotech.jukebox.service.ssl.SSLSocketFactory;
import com.budrotech.jukebox.service.ssl.TrustSelfSignedStrategy;
import com.budrotech.jukebox.util.CancellableTask;
import com.budrotech.jukebox.util.Constants;
import com.budrotech.jukebox.util.FileUtil;
import com.budrotech.jukebox.util.ProgressListener;
import com.budrotech.jukebox.util.Util;

import org.apache.http.conn.scheme.SocketFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.Response;

import static java.util.Arrays.asList;

/**
 * @author Sindre Mehus
 */
public class RESTMusicService implements MusicService
{

	private static final String TAG = RESTMusicService.class.getSimpleName();

	 static final int SOCKET_CONNECT_TIMEOUT = 10 * 1000;
	static final int SOCKET_READ_TIMEOUT_DEFAULT = 10 * 1000;
	private static final int SOCKET_READ_TIMEOUT_DOWNLOAD = 30 * 1000;
	private static final int SOCKET_READ_TIMEOUT_GET_RANDOM_SONGS = 60 * 1000;
	private static final int SOCKET_READ_TIMEOUT_GET_PLAYLIST = 60 * 1000;

	// Allow 20 seconds extra timeout per MB offset.
	private static final double TIMEOUT_MILLIS_PER_OFFSET_BYTE = 20000.0 / 1000000.0;

	/**
	 * URL from which to fetch latest versions.
	 */
	private static final String VERSION_URL = "http://subsonic.org/backend/version.view";

	static final int HTTP_REQUEST_MAX_ATTEMPTS = 5;

	public RESTMusicService()
	{
//
//		// Create and initialize scheme registry
//		SchemeRegistry schemeRegistry = new SchemeRegistry();
//		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
//		schemeRegistry.register(new Scheme("https", createSSLSocketFactory(), 443));
//
//		// Create an HttpClient with the ThreadSafeClientConnManager.
//		// This connection manager must be used if more than one thread will
//		// be using the HttpClient.
//		connManager = new ThreadSafeClientConnManager(params, schemeRegistry);
//		httpClient = new DefaultHttpClient(connManager, params);
	}

	private static SocketFactory createSSLSocketFactory()
	{
		try
		{
			return new SSLSocketFactory(new TrustSelfSignedStrategy(), SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		}
		catch (Throwable x)
		{
			Log.e(TAG, "Failed to create custom SSL socket factory, using default.", x);
			return org.apache.http.conn.ssl.SSLSocketFactory.getSocketFactory();
		}
	}

	public static HttpUrl.Builder getSubsonicUrl(Context context, String method) {
		SharedPreferences preferences = Util.getPreferences(context);

		int instance = preferences.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1);
		String serverUrl = preferences.getString(Constants.PREFERENCES_KEY_SERVER_URL + instance, null);
		String username = preferences.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null);
		String password = preferences.getString(Constants.PREFERENCES_KEY_PASSWORD + instance, null);

		// Slightly obfuscate password
		password = "enc:" + Util.utf8HexEncode(password);

		HttpUrl.Builder builder = HttpUrl.parse(serverUrl).newBuilder();
		builder.addPathSegment("rest");
		builder.addPathSegment(method + ".view");
		builder.addQueryParameter("u", username);
		builder.addQueryParameter("p", password);
		builder.addQueryParameter("v", Constants.REST_PROTOCOL_VERSION);
		builder.addQueryParameter("c", Constants.REST_CLIENT_ID);

		return builder;
	}

	@Override
	public void ping(Context context, ProgressListener progressListener) throws Exception
	{
		Reader reader = getReader(context, progressListener, "ping");
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public boolean isLicenseValid(Context context, ProgressListener progressListener) throws Exception
	{
		Reader reader = getReader(context, progressListener, "getLicense");
		try
		{
			ServerInfo serverInfo = new LicenseParser(context).parse(reader);
			return serverInfo.isLicenseValid();
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public List<MusicFolder> getMusicFolders(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		List<MusicFolder> cachedMusicFolders = readCachedMusicFolders(context);
		if (cachedMusicFolders != null && !refresh)
		{
			return cachedMusicFolders;
		}

		Reader reader = getReader(context, progressListener, "getMusicFolders");
		try
		{
			List<MusicFolder> musicFolders = new MusicFoldersParser(context).parse(reader, progressListener);
			writeCachedMusicFolders(context, musicFolders);
			return musicFolders;
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		Indexes cachedIndexes = readCachedIndexes(context, musicFolderId);
		if (cachedIndexes != null && !refresh)
		{
			return cachedIndexes;
		}

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		if (musicFolderId != null)
		{
			parameterNames.add("musicFolderId");
			parameterValues.add(musicFolderId);
		}

		Reader reader = getReader(context, progressListener, "getIndexes", parameterNames, parameterValues);

		try
		{
			Indexes indexes = new IndexesParser(context).parse(reader, progressListener);
			if (indexes != null)
			{
				writeCachedIndexes(context, indexes, musicFolderId);
				return indexes;
			}

			return cachedIndexes != null ? cachedIndexes : new Indexes(0, null, new ArrayList<Artist>(), new ArrayList<Artist>());
		}
		finally
		{
			Util.close(reader);
		}
	}

	private static Indexes readCachedIndexes(Context context, String musicFolderId)
	{
		String filename = getCachedIndexesFilename(context, musicFolderId);
		return FileUtil.deserialize(context, filename);
	}

	private static void writeCachedIndexes(Context context, Indexes indexes, String musicFolderId)
	{
		String filename = getCachedIndexesFilename(context, musicFolderId);
		FileUtil.serialize(context, indexes, filename);
	}

	private static String getCachedIndexesFilename(Context context, String musicFolderId)
	{
		String s = Util.getRestUrl(context, null) + musicFolderId;
		return String.format("indexes-%d.ser", Math.abs(s.hashCode()));
	}

	@Override
	public Indexes getArtists(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Artists by ID3 tag not supported.");

		Indexes cachedArtists = readCachedArtists(context);
		if (cachedArtists != null && !refresh)
		{
			return cachedArtists;
		}

		Reader reader = getReader(context, progressListener, "getArtists");
		try
		{
			Indexes indexes = new IndexesParser(context).parse(reader, progressListener);
			if (indexes != null)
			{
				writeCachedArtists(context, indexes);
				return indexes;
			}

			return cachedArtists != null ? cachedArtists : new Indexes(0, null, new ArrayList<Artist>(), new ArrayList<Artist>());
		}
		finally
		{
			Util.close(reader);
		}
	}

	private static Indexes readCachedArtists(Context context)
	{
		String filename = getCachedArtistsFilename(context);
		return FileUtil.deserialize(context, filename);
	}

	private static void writeCachedArtists(Context context, Indexes artists)
	{
		String filename = getCachedArtistsFilename(context);
		FileUtil.serialize(context, artists, filename);
	}

	private static String getCachedArtistsFilename(Context context)
	{
		String s = Util.getRestUrl(context, null);
		return String.format("indexes-%d.ser", Math.abs(s.hashCode()));
	}

	private static ArrayList<MusicFolder> readCachedMusicFolders(Context context)
	{
		String filename = getCachedMusicFoldersFilename(context);
		return FileUtil.deserialize(context, filename);
	}

	private static void writeCachedMusicFolders(Context context, List<MusicFolder> musicFolders)
	{
		String filename = getCachedMusicFoldersFilename(context);
		FileUtil.serialize(context, new ArrayList<MusicFolder>(musicFolders), filename);
	}

	private static String getCachedMusicFoldersFilename(Context context)
	{
		String s = Util.getRestUrl(context, null);
		return String.format("musicFolders-%d.ser", Math.abs(s.hashCode()));
	}

	@Override
	public void star(String id, String albumId, String artistId, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Starring not supported.");

		List<String> parameterNames = new LinkedList<String>();
		List<Object> parameterValues = new LinkedList<Object>();

		if (id != null)
		{
			parameterNames.add("id");
			parameterValues.add(id);
		}

		if (albumId != null)
		{
			parameterNames.add("albumId");
			parameterValues.add(albumId);
		}

		if (artistId != null)
		{
			parameterNames.add("artistId");
			parameterValues.add(artistId);
		}

		Reader reader = getReader(context, progressListener, "star", parameterNames, parameterValues);
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void unstar(String id, String albumId, String artistId, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Unstarring not supported.");

		List<String> parameterNames = new LinkedList<String>();
		List<Object> parameterValues = new LinkedList<Object>();

		if (id != null)
		{
			parameterNames.add("id");
			parameterValues.add(id);
		}

		if (albumId != null)
		{
			parameterNames.add("albumId");
			parameterValues.add(albumId);
		}

		if (artistId != null)
		{
			parameterNames.add("artistId");
			parameterValues.add(artistId);
		}


		Reader reader = getReader(context, progressListener, "unstar", parameterNames, parameterValues);
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getMusicDirectory(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		Reader reader = getReaderForId(context, progressListener, "getMusicDirectory", id);
		try
		{
			return new MusicDirectoryParser(context).parse(name, reader, progressListener, false);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getArtist(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Artist by ID3 tag not supported.");

		Reader reader = getReaderForId(context, progressListener, "getArtist", id);
		try
		{
			return new MusicDirectoryParser(context).parse(name, reader, progressListener, false);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getAlbum(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Album by ID3 tag not supported.");

		Reader reader = getReaderForId(context, progressListener, "getAlbum", id);
		try
		{
			return new MusicDirectoryParser(context).parse(name, reader, progressListener, true);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public SearchResult search(SearchCriteria criteria, Context context, ProgressListener progressListener) throws Exception
	{
		try
		{
			return !Util.isOffline(context) && Util.getShouldUseId3Tags(context) ? search3(criteria, context, progressListener) : search2(criteria, context, progressListener);
		}
		catch (ServerTooOldException x)
		{
			// Ensure backward compatibility with REST 1.3.
			return searchOld(criteria, context, progressListener);
		}
	}

	/**
	 * Search using the "search" REST method.
	 */
	private SearchResult searchOld(SearchCriteria criteria, Context context, ProgressListener progressListener) throws Exception
	{
		List<String> parameterNames = asList("any", "songCount");
		List<Object> parameterValues = Arrays.<Object>asList(criteria.getQuery(), criteria.getSongCount());
		Reader reader = getReader(context, progressListener, "search", parameterNames, parameterValues);
		try
		{
			return new SearchResultParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	/**
	 * Search using the "search2" REST method, available in 1.4.0 and later.
	 */
	private SearchResult search2(SearchCriteria criteria, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.4", "Search2 not supported.");

		List<String> parameterNames = asList("query", "artistCount", "albumCount", "songCount");
		List<Object> parameterValues = Arrays.<Object>asList(criteria.getQuery(), criteria.getArtistCount(), criteria.getAlbumCount(), criteria.getSongCount());
		Reader reader = getReader(context, progressListener, "search2", parameterNames, parameterValues);
		try
		{
			return new SearchResult2Parser(context).parse(reader, progressListener, false);
		}
		finally
		{
			Util.close(reader);
		}
	}

	private SearchResult search3(SearchCriteria criteria, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Searching by ID3 tag not supported.");

		List<String> parameterNames = asList("query", "artistCount", "albumCount", "songCount");
		List<Object> parameterValues = Arrays.<Object>asList(criteria.getQuery(), criteria.getArtistCount(), criteria.getAlbumCount(), criteria.getSongCount());
		Reader reader = getReader(context, progressListener, "search3", parameterNames, parameterValues);
		try
		{
			return new SearchResult2Parser(context).parse(reader, progressListener, true);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getPlaylist(String id, String name, Context context, ProgressListener progressListener) throws Exception
	{
		SubsonicRequest request = new SubsonicRequest(context, "getPlaylist")
				.addQueryParameter("id", id)
				.setSocketReadTimeout(SOCKET_READ_TIMEOUT_GET_PLAYLIST);

		Reader reader = request.getResponse(progressListener, null).body().charStream();
		try
		{
			MusicDirectory playlist = new PlaylistParser(context).parse(reader, progressListener);

			File playlistFile = FileUtil.getPlaylistFile(Util.getServerName(context), name);
			FileWriter fw = new FileWriter(playlistFile);
			BufferedWriter bw = new BufferedWriter(fw);
			try
			{
				fw.write("#EXTM3U\n");
				for (MusicDirectory.Entry e : playlist.getChildren())
				{
					String filePath = FileUtil.getSongFile(context, e).getAbsolutePath();
					if (!new File(filePath).exists())
					{
						String ext = FileUtil.getExtension(filePath);
						String base = FileUtil.getBaseName(filePath);
						filePath = base + ".complete." + ext;
					}
					fw.write(filePath + '\n');
				}
			}
			catch (Exception e)
			{
				Log.w(TAG, "Failed to save playlist: " + name);
			}
			finally
			{
				bw.close();
				fw.close();
			}

			return playlist;
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		Reader reader = getReader(context, progressListener, "getPlaylists");
		try
		{
			return new PlaylistsParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries, Context context, ProgressListener progressListener) throws Exception
	{
		List<String> parameterNames = new LinkedList<String>();
		List<Object> parameterValues = new LinkedList<Object>();

		if (id != null)
		{
			parameterNames.add("playlistId");
			parameterValues.add(id);
		}
		if (name != null)
		{
			parameterNames.add("name");
			parameterValues.add(name);
		}
		for (MusicDirectory.Entry entry : entries)
		{
			parameterNames.add("songId");
			parameterValues.add(entry.getId());
		}

		Reader reader = getReader(context, progressListener, "createPlaylist", parameterNames, parameterValues);
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void deletePlaylist(String id, Context context, ProgressListener progressListener) throws Exception
	{
		Reader reader = getReaderForId(context, progressListener, "deletePlaylist", id);
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void updatePlaylist(String id, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Updating playlist not supported.");

		List<String> names = new ArrayList<String>();
		List<Object> values = new ArrayList<Object>();
		names.add("playlistId");
		values.add(id);
		for (MusicDirectory.Entry song : toAdd)
		{
			names.add("songIdToAdd");
			values.add(song.getId());
		}
		Reader reader = getReader(context, progressListener, "updatePlaylist", names, values);
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void removeFromPlaylist(String id, List<Integer> toRemove, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Updating playlists is not supported.");
		List<String> names = new ArrayList<String>();
		List<Object> values = new ArrayList<Object>();
		names.add("playlistId");
		values.add(id);
		for (Integer song : toRemove)
		{
			names.add("songIndexToRemove");
			values.add(song);
		}
		Reader reader = getReader(context, progressListener, "updatePlaylist", names, values);
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void updatePlaylist(String id, String name, String comment, boolean pub, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Updating playlists is not supported.");
		Reader reader = getReader(context, progressListener, "updatePlaylist", asList("playlistId", "name", "comment", "public"), Arrays.<Object>asList(id, name, comment, pub));
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public Lyrics getLyrics(String artist, String title, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.2", "Lyrics not supported.");

		Reader reader = getReader(context, progressListener, "getLyrics", asList("artist", "title"), Arrays.<Object>asList(artist, title));
		try
		{
			return new LyricsParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void scrobble(String id, boolean submission, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.5", "Scrobbling not supported.");

		Reader reader = getReader(context, progressListener, "scrobble", asList("id", "submission"), Arrays.<Object>asList(id, submission));
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getAlbumList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.2", "Album list not supported.");

		Reader reader = getReader(context, progressListener, "getAlbumList", asList("type", "size", "offset"), Arrays.<Object>asList(type, size, offset));
		try
		{
			return new AlbumListParser(context).parse(reader, progressListener, false);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getAlbumList2(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Album list by ID3 tag not supported.");

		Reader reader = getReader(context, progressListener, "getAlbumList2", asList("type", "size", "offset"), Arrays.<Object>asList(type, size, offset));
		try
		{
			return new AlbumListParser(context).parse(reader, progressListener, true);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getRandomSongs(int size, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.2", "Random songs not supported.");

		SubsonicRequest request = new SubsonicRequest(context, "getRandomSongs");
		request.setSocketReadTimeout(SOCKET_READ_TIMEOUT_GET_RANDOM_SONGS);
		request.addQueryParameter("size", String.valueOf(size));

		Reader reader = request.getResponse(progressListener, null).body().charStream();
		try
		{
			return new RandomSongsParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public SearchResult getStarred(Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Starred albums not supported.");

		Reader reader = getReader(context, progressListener, "getStarred");
		try
		{
			return new SearchResult2Parser(context).parse(reader, progressListener, false);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public SearchResult getStarred2(Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Starred albums by ID3 tag not supported.");

		Reader reader = getReader(context, progressListener, "getStarred2");
		try
		{
			return new SearchResult2Parser(context).parse(reader, progressListener, true);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public Version getLocalVersion(Context context) throws Exception
	{
		return new Version(Util.getVersionName(context));
	}

	private static void checkServerVersion(Context context, String version, String text) throws ServerTooOldException
	{
		Version serverVersion = Util.getServerRestVersion(context);
		Version requiredVersion = new Version(version);
		boolean ok = serverVersion == null || serverVersion.compareTo(requiredVersion) >= 0;

		if (!ok)
		{
			throw new ServerTooOldException(text);
		}
	}

	private static boolean checkServerVersion(Context context, String version)
	{
		Version serverVersion = Util.getServerRestVersion(context);
		Version requiredVersion = new Version(version);
		return serverVersion == null || serverVersion.compareTo(requiredVersion) >= 0;
	}

	public static boolean isErrorResponse(Response response) {
		String contentType = response.body().contentType().toString();
		return contentType != null && contentType.startsWith("text/xml");
	}
	
	
	@Override
	public Bitmap getCoverArt(Context context, final MusicDirectory.Entry entry, int size, boolean saveToFile, boolean highQuality, ProgressListener progressListener) throws Exception
	{
		// Synchronize on the entry so that we don't download concurrently for
		// the same song.
		if (entry == null)
		{
			return null;
		}

		synchronized (entry)
		{
			// Use cached file, if existing.
			Bitmap bitmap = FileUtil.getAlbumArtBitmap(context, entry, size, highQuality);
			boolean serverScaling = Util.isServerScalingEnabled(context);

			if (bitmap == null)
			{
				SubsonicRequest subsonicRequest = new SubsonicRequest(context, "getCoverArt");

				InputStream in = null;
				try
				{
					subsonicRequest.addQueryParameter("id", entry.getCoverArt());
					if (serverScaling) {
						subsonicRequest.addQueryParameter("size", String.valueOf(size));
					}

					Response response = subsonicRequest.getResponse(progressListener, null);
					in = response.body().byteStream();

					// If content type is XML, an error occurred. Get it.
					if (isErrorResponse(response))
					{
						new ErrorParser(context).parse(new InputStreamReader(in, Constants.UTF_8));
						return null; // Never reached.
					}

					byte[] bytes = Util.toByteArray(in);

					// If we aren't allowing server-side scaling, always save the file to disk because it will be unmodified
					if (!serverScaling || saveToFile)
					{
						OutputStream out = null;

						try
						{
							out = new FileOutputStream(FileUtil.getAlbumArtFile(context, entry));
							out.write(bytes);
						}
						finally
						{
							Util.close(out);
						}
					}

					bitmap = FileUtil.getSampledBitmap(bytes, size, highQuality);
				}
				finally
				{
					Util.close(in);
				}
			}

			// Return scaled bitmap
			return Util.scaleBitmap(bitmap, size);
		}
	}

	@Override
	public Response getDownloadInputStream(Context context, MusicDirectory.Entry song, long offset, int maxBitrate, CancellableTask task) throws Exception
	{
		SubsonicRequest request = new SubsonicRequest(context, "stream");
		request.addQueryParameter("id", song.getId());
		request.addQueryParameter("maxBitRate", String.valueOf(maxBitrate));

		// Set socket read timeout. Note: The timeout increases as the offset gets larger. This is
		// to avoid the thrashing effect seen when offset is combined with transcoding/downsampling on the server.
		// In that case, the server uses a long time before sending any data, causing the client to time out.
		request.setSocketReadTimeout((int) (SOCKET_READ_TIMEOUT_DOWNLOAD + offset * TIMEOUT_MILLIS_PER_OFFSET_BYTE));

		if (offset > 0)
		{
			request.addBasicHeader("Range", String.format("bytes=%d-", offset));
		}

		Response response = request.getResponse(null, task);

		// If content type is XML, an error occurred.  Get it.
		if (isErrorResponse(response))
		{
			Reader in = response.body().charStream();
			try
			{
				new ErrorParser(context).parse(in);
			}
			finally
			{
				Util.close(in);
			}
		}

		return response;
	}

	@Override
	public JukeboxStatus updateJukeboxPlaylist(List<String> ids, Context context, ProgressListener progressListener) throws Exception
	{
		int n = ids.size();
		List<String> parameterNames = new ArrayList<String>(n + 1);
		parameterNames.add("action");

		for (String ignored : ids)
		{
			parameterNames.add("id");
		}

		List<Object> parameterValues = new ArrayList<Object>();
		parameterValues.add("set");
		parameterValues.addAll(ids);

		return executeJukeboxCommand(context, progressListener, parameterNames, parameterValues);
	}

	@Override
	public JukeboxStatus skipJukebox(int index, int offsetSeconds, Context context, ProgressListener progressListener) throws Exception
	{
		List<String> parameterNames = asList("action", "index", "offset");
		List<Object> parameterValues = Arrays.<Object>asList("skip", index, offsetSeconds);
		return executeJukeboxCommand(context, progressListener, parameterNames, parameterValues);
	}

	@Override
	public JukeboxStatus stopJukebox(Context context, ProgressListener progressListener) throws Exception
	{
		return executeJukeboxCommand(context, progressListener, Collections.singletonList("action"), Collections.<Object>singletonList("stop"));
	}

	@Override
	public JukeboxStatus startJukebox(Context context, ProgressListener progressListener) throws Exception
	{
		return executeJukeboxCommand(context, progressListener, Collections.singletonList("action"), Collections.<Object>singletonList("start"));
	}

	@Override
	public JukeboxStatus getJukeboxStatus(Context context, ProgressListener progressListener) throws Exception
	{
		return executeJukeboxCommand(context, progressListener, Collections.singletonList("action"), Collections.<Object>singletonList("status"));
	}

	@Override
	public JukeboxStatus setJukeboxGain(float gain, Context context, ProgressListener progressListener) throws Exception
	{
		List<String> parameterNames = asList("action", "gain");
		List<Object> parameterValues = Arrays.<Object>asList("setGain", gain);
		return executeJukeboxCommand(context, progressListener, parameterNames, parameterValues);
	}

	@Override
	public List<Share> getShares(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.6", "Shares not supported.");
		Reader reader = getReader(context, progressListener, "getShares");
		try
		{
			return new ShareParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	private JukeboxStatus executeJukeboxCommand(Context context, ProgressListener progressListener, List<String> parameterNames, List<Object> parameterValues) throws Exception
	{
		checkServerVersion(context, "1.7", "Jukebox not supported.");
		Reader reader = getReader(context, progressListener, "jukeboxControl", parameterNames, parameterValues);
		try
		{
			return new JukeboxStatusParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	private Reader getReader(Context context, ProgressListener progressListener, String method) throws Exception
	{
		SubsonicRequest request = new SubsonicRequest(context, method);
		return request.getResponse(progressListener, null).body().charStream();
	}

	private Reader getReaderForId(Context context, ProgressListener progressListener, String method, String id) throws Exception
	{
		SubsonicRequest request = new SubsonicRequest(context, method);
		request.addQueryParameter("id", id);
		return request.getResponse(progressListener, null).body().charStream();
	}

	private Reader getReader(Context context, ProgressListener progressListener, String method, List<String> parameterNames, List<Object> parameterValues) throws Exception
	{

		if (progressListener != null)
		{
			progressListener.updateProgress(R.string.service_connecting);
		}

		SubsonicRequest request = new SubsonicRequest(context, method);

		// If not too many parameters, extract them to the URL rather than
		// relying on the HTTP POST request being
		// received intact. Remember, HTTP POST requests are converted to GET
		// requests during HTTP redirects, thus
		// loosing its entity.

		if (parameterNames != null)
		{
			for (int i = 0; i < parameterNames.size(); i++)
				{
					request.addQueryParameter(parameterNames.get(i), String.valueOf(parameterValues.get(i)));
				}
		}

		return request.getResponse(progressListener, null).body().charStream();
	}

	@Override
	public List<Genre> getGenres(Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.9", "Genres not supported.");

		Reader reader = getReader(context, progressListener, "getGenres");
		try
		{
			return new GenreParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.9", "Genres not supported.");

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("genre");
		parameterValues.add(genre);
		parameterNames.add("count");
		parameterValues.add(count);
		parameterNames.add("offset");
		parameterValues.add(offset);

		Reader reader = getReader(context, progressListener, "getSongsByGenre", parameterNames, parameterValues);

		try
		{
			return new RandomSongsParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public UserInfo getUser(String username, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.3", "getUser not supported.");

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("username");
		parameterValues.add(username);

		Reader reader = getReader(context, progressListener, "getUser", parameterNames, parameterValues);

		try
		{
			return new UserInfoParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public List<ChatMessage> getChatMessages(Long since, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.2", "Chat not supported.");

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("since");
		parameterValues.add(since);

		Reader reader = getReader(context, progressListener, "getChatMessages", parameterNames, parameterValues);

		try
		{
			return new ChatMessageParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void addChatMessage(String message, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.2", "Chat not supported.");

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("message");
		parameterValues.add(message);

		Reader reader = getReader(context, progressListener, "addChatMessage", parameterNames, parameterValues);

		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public List<Bookmark> getBookmarks(Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.9", "Bookmarks not supported.");

		Reader reader = getReader(context, progressListener, "getBookmarks");

		try
		{
			return new BookmarkParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void createBookmark(String id, int position, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.9", "Bookmarks not supported.");

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("id");
		parameterValues.add(id);
		parameterNames.add("position");
		parameterValues.add(position);

		Reader reader = getReader(context, progressListener, "createBookmark", parameterNames, parameterValues);

		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void deleteBookmark(String id, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.9", "Bookmarks not supported.");

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("id");
		parameterValues.add(id);

		Reader reader = getReader(context, progressListener, "deleteBookmark", parameterNames, parameterValues);

		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public List<Share> createShare(List<String> ids, String description, Long expires, Context context, ProgressListener progressListener) throws Exception
	{
		List<String> parameterNames = new LinkedList<String>();
		List<Object> parameterValues = new LinkedList<Object>();

		for (String id : ids)
		{
			parameterNames.add("id");
			parameterValues.add(id);
		}

		if (description != null)
		{
			parameterNames.add("description");
			parameterValues.add(description);
		}

		if (expires > 0)
		{
			parameterNames.add("expires");
			parameterValues.add(expires);
		}

		Reader reader = getReader(context, progressListener, "createShare", parameterNames, parameterValues);
		try
		{
			return new ShareParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void deleteShare(String id, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.6", "Shares not supported.");

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("id");
		parameterValues.add(id);

		Reader reader = getReader(context, progressListener, "deleteShare", parameterNames, parameterValues);

		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void updateShare(String id, String description, Long expires, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.6", "Updating share not supported.");

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("id");
		parameterValues.add(id);

		if (description != null)
		{
			parameterNames.add("description");
			parameterValues.add(description);
		}

		if (expires > 0)
		{
			parameterNames.add("expires");
			parameterValues.add(expires);
		}

		Reader reader = getReader(context, progressListener, "updateShare", parameterNames, parameterValues);
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public Bitmap getAvatar(Context context, String username, int size, boolean saveToFile, boolean highQuality, ProgressListener progressListener) throws Exception
	{
		// Return silently if server is too old
		if (!checkServerVersion(context, "1.8"))
			return null;

		// Synchronize on the username so that we don't download concurrently for
		// the same user.
		if (username == null)
		{
			return null;
		}

		synchronized (username)
		{
			// Use cached file, if existing.
			Bitmap bitmap = FileUtil.getAvatarBitmap(username, size, highQuality);

			if (bitmap == null)
			{
				InputStream in = null;

				try
				{
					SubsonicRequest request = new SubsonicRequest(context, "getAvatar");
					request.addQueryParameter("username", username);
					Response response = request.getResponse(progressListener, null);
					in = response.body().byteStream();

					// If content type is XML, an error occurred. Get it.
					if (isErrorResponse(response))
					{
						new ErrorParser(context).parse(new InputStreamReader(in, Constants.UTF_8));
						return null; // Never reached.
					}

					byte[] bytes = Util.toByteArray(in);

					// If we aren't allowing server-side scaling, always save the file to disk because it will be unmodified
					if (saveToFile)
					{
						OutputStream out = null;

						try
						{
							out = new FileOutputStream(FileUtil.getAvatarFile(username));
							out.write(bytes);
						}
						finally
						{
							Util.close(out);
						}
					}

					bitmap = FileUtil.getSampledBitmap(bytes, size, highQuality);
				}
				finally
				{
					Util.close(in);
				}
			}

			// Return scaled bitmap
			return Util.scaleBitmap(bitmap, size);
		}
	}
}
