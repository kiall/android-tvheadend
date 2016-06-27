package ie.macinnes.tvheadend.setup;

import android.app.Activity;
import android.os.Bundle;

import ie.macinnes.tvheadend.R;
import us.feras.mdv.MarkdownView;

public class IssuesActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_issues);

        MarkdownView markdownView = (MarkdownView) findViewById(R.id.markdownView);
        markdownView.loadMarkdownFile("https://raw.githubusercontent.com/kiall/android-tvheadend/master/ISSUES.md");
    }
}
