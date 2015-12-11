package com.budrotech.jukebox.activity;

import android.app.Activity;
import android.content.Intent;

import com.budrotech.jukebox.util.Constants;

/**
 * Created by Joshua Bahnsen on 12/30/13.
 */
public class ResultActivity extends Activity
{
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (resultCode)
		{
			case Constants.RESULT_CLOSE_ALL:
				setResult(Constants.RESULT_CLOSE_ALL);
				finish();
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

}
