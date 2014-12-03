package uk.co.johnsto.mailcircle.widgets;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.preference.ListPreference;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import uk.co.johnsto.mailcircle.R;

/**
 * Preference that allows the user to select a colour from Google's
 * 'Material' palette, prefixing each with a coloured bullet point
 * and the colour name. It expects the color value to be in hexadecimal
 * form.
 */
public class ColorListPreference extends ListPreference {
    private int mClickedDialogEntryIndex;

    public ColorListPreference(Context context) {
        super(context);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        // Array adapter containing <color name> => <color value> pairs
        ArrayAdapter<Pair<String, Integer>> adapter = new ArrayAdapter<Pair<String, Integer>>(
                getContext(), android.R.layout.select_dialog_singlechoice) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                Pair<String, Integer> pair = getItem(position);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);

                // Style entry with a coloured bullet
                SpannableStringBuilder text = new SpannableStringBuilder("\u2B24 " + pair.first);
                text.setSpan(new ForegroundColorSpan(pair.second), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                textView.setText(text);

                return view;
            }
        };

        // Populate adapter with color Name/Hex pairs
        Resources res = getContext().getResources();
        String[] colorNames = res.getStringArray(R.array.pref_colors);
        String[] colorValues = res.getStringArray(R.array.pref_colors_values);
        for (int i = 0; i < colorNames.length; i++) {
            String text = colorNames[i];
            int color = Color.parseColor(colorValues[i]);
            adapter.add(new Pair<String, Integer>(text, color));
        }

        // Set value to the current preference
        String value = getValue();
        int selectedIndex = findIndexOfValue(value);
        builder.setSingleChoiceItems(adapter, selectedIndex, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mClickedDialogEntryIndex = which;
                ColorListPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                dialog.dismiss();
            }
        });

    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        // Ripped from ListPreference; set the result
        CharSequence[] entryValues = getEntryValues();
        if (positiveResult && mClickedDialogEntryIndex >= 0 && entryValues != null) {
            String value = entryValues[mClickedDialogEntryIndex].toString();
            if (callChangeListener(value)) {
                setValue(value);
            }
        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        TextView summary = (TextView) view.findViewById(android.R.id.summary);

        // Get entry name and colour value
        String entry = getEntry().toString();
        int color = Color.parseColor(getValue());

        // Set summary to the chosen colour
        SpannableStringBuilder text = new SpannableStringBuilder("\u2B24 " + entry);
        text.setSpan(new ForegroundColorSpan(color), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        summary.setText(text);
        summary.setVisibility(View.VISIBLE);
    }
}
