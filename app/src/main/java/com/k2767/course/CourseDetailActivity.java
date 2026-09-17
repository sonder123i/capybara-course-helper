package com.k2767.course;

import androidx.appcompat.app.AppCompatActivity;

public class CourseDetailActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context context) {
        super.attachBaseContext(com.k2767.course.manager.AppThemeCoordinator.INSTANCE.wrapContext(context));
    }
}
