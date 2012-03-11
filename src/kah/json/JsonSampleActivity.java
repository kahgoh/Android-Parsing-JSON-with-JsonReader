package kah.json;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.json.JSONException;
import org.kah.R;

import android.app.Activity;
import android.os.Bundle;
import android.os.Debug;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TextView;

/**
 * Activity parsing the sample JSON data from the Bureau of Meteorology
 * (http://www.bom.gov.au) and displays it in a table on the screen, using
 * {@link JsonReader}.
 * 
 * @author Kah
 */
public class JsonSampleActivity extends Activity {
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onStart() {
		super.onStart();

		// Downloading the RSS feed needs to be done on a separate thread.
		Thread downloadThread = new Thread(new Runnable() {

			public void run() {
				Debug.startMethodTracing("JsonReader");
				loadData();
				Debug.stopMethodTracing();
			}
		}, "Reading Thread");

		downloadThread.start();
	}

	/**
	 * Reads the sample JSON data and loads it into a table.
	 */
	private void loadData() {
		JsonReader reader = null;
		try {
			InputStream inStream = getResources().openRawResource(R.raw.json);

			BufferedInputStream bufferedStream = new BufferedInputStream(
					inStream);
			InputStreamReader streamReader = new InputStreamReader(
					bufferedStream);

			reader = new JsonReader(streamReader);

			populateTable(reader);
		} catch (Exception e) {
			Log.e("Json Sample", e.getLocalizedMessage(), e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					// Do nothing
				}
			}
		}
	}

	/**
	 * Populates the table with the JSON data.
	 * 
	 * @param reader
	 *            the {@link JsonReader} used to read in the data
	 * @throws JSONException
	 * @throws IOException
	 */
	private void populateTable(JsonReader reader) throws JSONException,
			IOException {
		// Search for the data array.
		boolean hasData = findArray(reader, "data");

		if (hasData) {
			parseDataArray(reader);
		}
	}

	/**
	 * Parses an array in the JSON data stream.
	 * 
	 * @param reader
	 *            the {@link JsonReader} used to read in the data
	 * @throws IOException
	 */
	private void parseDataArray(JsonReader reader) throws IOException {
		final TableLayout table = (TableLayout) findViewById(R.id.table);
		reader.beginArray();

		JsonToken token = reader.peek();
		while (token != JsonToken.END_ARRAY) {
			parseDataObject(reader, table);
			token = reader.peek();
		}
	}

	/**
	 * Parses an object in the JSON data stream.
	 * 
	 * @param reader
	 *            the {@link JsonReader} used to read in the data
	 * @param table
	 *            the {@link TableLayout} to add the read data to
	 * @throws IOException
	 */
	private void parseDataObject(JsonReader reader, final TableLayout table)
			throws IOException {
		if (findNextTokenType(reader, JsonToken.BEGIN_OBJECT)) {
			final View row = getLayoutInflater().inflate(R.layout.rows, null);

			reader.beginObject();
			while (reader.hasNext()) {
				parseData(reader, row);
			}

			table.post(new Runnable() {

				public void run() {
					table.addView(row);
				}
			});

			// Consume end of object so that we can just look for the start of
			// the next object on the next call.
			if (findNextTokenType(reader, JsonToken.END_OBJECT)) {
				reader.endObject();
			}
		}
	}

	/**
	 * Loads the next observation into the given table.
	 * 
	 * @param reader
	 *            the {@link JsonReader} containing the observation
	 * @param row
	 *            the row to place the data in
	 * @throws IOException
	 */
	private void parseData(JsonReader reader, View row) throws IOException {
		int columnId = toRowId(reader.nextName());

		if (columnId != -1) {
			((TextView) row.findViewById(columnId))
					.setText(reader.nextString());
		} else {
			consume(reader, reader.peek());
		}
	}

	/**
	 * Provides the id of the cell for a given field in the observation data.
	 * 
	 * @param name
	 *            the name of the observation field
	 * @return the id of the cell that the field should be placed in or -1, if
	 *         the field should not be added to the row
	 */
	private int toRowId(String name) {
		if (name.equals("local_date_time_full")) {
			return R.id.localTime;
		} else if (name.equals("apparent_t")) {
			return R.id.apprentTemp;
		} else if (name.equals("wind_spd_kmh")) {
			return R.id.windSpeed;
		}
		return -1;
	}

	/**
	 * Consumes data from the given {@link JsonReader} until the array with the
	 * given name is found.
	 * 
	 * @param reader
	 *            the instance of {@link JsonReader} for reading the JSON data
	 * @param objectName
	 *            the name of the array to find
	 * @return {@code true} if the array was found, otherwise {@code false}
	 * @throws IOException
	 */
	private boolean findArray(JsonReader reader, String objectName)
			throws IOException {
		while (findNextTokenType(reader, JsonToken.NAME)) {

			String name = reader.nextName();
			if (name.equals(objectName)) {
				JsonToken token = reader.peek();
				if (token == JsonToken.BEGIN_ARRAY) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Consumes data from the given {@link JsonReader} until the next occurrence
	 * of the given token type is found.
	 * 
	 * @param reader
	 *            the data to consume from
	 * @param type
	 *            the type of token to find
	 * @return {@code true} if the next occurrence was found, otherwise
	 *         {@code false}
	 * @throws IOException
	 */
	private boolean findNextTokenType(JsonReader reader, JsonToken type)
			throws IOException {

		JsonToken token = reader.peek();
		while (token != JsonToken.END_DOCUMENT) {
			if (token == type) {
				return true;
			}

			consume(reader, token);
			token = reader.peek();
		}

		return false;
	}

	/**
	 * Consumes tokens from the reader.
	 * 
	 * @param reader
	 *            the instance of the reader
	 * @param type
	 *            the type of token to expect
	 * @throws IOException
	 */
	private void consume(JsonReader reader, JsonToken type) throws IOException {
		switch (type) {
		case BEGIN_ARRAY:
			reader.beginArray();
			break;
		case BEGIN_OBJECT:
			reader.beginObject();
			break;
		case END_ARRAY:
			reader.endArray();
			break;
		case END_OBJECT:
			reader.endObject();
			break;
		default:
			reader.skipValue();
		}
	}
}