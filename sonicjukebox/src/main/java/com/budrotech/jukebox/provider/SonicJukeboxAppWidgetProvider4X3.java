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

 Copyright 2010 (C) Sindre Mehus
 */
package com.budrotech.jukebox.provider;

import org.moire.jukebox.R;

public class SonicJukeboxAppWidgetProvider4X3 extends SonicJukeboxAppWidgetProvider
{

	public SonicJukeboxAppWidgetProvider4X3()
	{
		super();
		this.layoutId = R.layout.appwidget4x3;
	}

	private static SonicJukeboxAppWidgetProvider4X3 instance;

	public static synchronized SonicJukeboxAppWidgetProvider4X3 getInstance()
	{
		if (instance == null)
		{
			instance = new SonicJukeboxAppWidgetProvider4X3();
		}
		return instance;
	}
}
