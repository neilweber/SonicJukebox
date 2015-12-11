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
package com.budrotech.jukebox.view;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.budrotech.jukebox.R;
import com.budrotech.jukebox.domain.MusicDirectory;
import com.budrotech.jukebox.service.DownloadServiceImpl;
import com.budrotech.jukebox.service.MusicService;
import com.budrotech.jukebox.service.MusicServiceFactory;
import com.budrotech.jukebox.util.ImageLoader;

/**
 * Used to display albums in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class AlbumView extends UpdateView
{
	private static final String TAG = AlbumView.class.getSimpleName();
	private MusicDirectory.Entry entry;
	private EntryAdapter.AlbumViewHolder viewHolder;
	private ImageLoader imageLoader;

	public AlbumView(Context context, ImageLoader imageLoader)
	{
		super(context);
		this.imageLoader = imageLoader;
	}

	public void setLayout()
	{
		LayoutInflater.from(getContext()).inflate(R.layout.album_list_item, this, true);
		viewHolder = new EntryAdapter.AlbumViewHolder();
		viewHolder.title = (TextView) findViewById(R.id.album_title);
		viewHolder.artist = (TextView) findViewById(R.id.album_artist);
		viewHolder.cover_art = (ImageView) findViewById(R.id.album_coverart);
		viewHolder.add_to_queue = (ImageView) findViewById(R.id.album_add_to_queue);
		setTag(viewHolder);
	}

	public void setViewHolder(EntryAdapter.AlbumViewHolder viewHolder)
	{
		this.viewHolder = viewHolder;
		this.viewHolder.cover_art.invalidate();
		setTag(this.viewHolder);
	}

	public MusicDirectory.Entry getEntry()
	{
		return this.entry;
	}

	public void setAlbum(final MusicDirectory.Entry album)
	{
		viewHolder.cover_art.setTag(album);
		imageLoader.loadImage(viewHolder.cover_art, album, false, 0, false, true);
		this.entry = album;

		String title = album.getTitle();
		String artist = album.getArtist();

		viewHolder.title.setText(title);
		viewHolder.artist.setText(artist);
		viewHolder.artist.setVisibility(artist == null ? GONE : VISIBLE);

		if ("-1".equals(album.getId()))
		{
			viewHolder.add_to_queue.setVisibility(GONE);
		}
		else
		{
			viewHolder.add_to_queue.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								Looper.prepare();

								MusicService musicService = MusicServiceFactory.getMusicService(getContext());
								MusicDirectory musicDirectory = musicService.getMusicDirectory(album.getId(), album.getArtist(), false, getContext(), null);
								DownloadServiceImpl.getDownloadService(getContext()).download(getContext(), musicDirectory.getChildren(), true, false, false, false, false);
							} catch (Exception e) {
								Log.e(TAG, "Error queuing up songs in album " + album.getId(), e);
							} finally {
								Looper.loop();
							}
						}
					}).start();
				}
			});
		}
	}
}