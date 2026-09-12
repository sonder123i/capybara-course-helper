package com.tyust.course;

import androidx.appcompat.app.AppCompatActivity;

public class CourseDetailActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context context) {
        super.attachBaseContext(com.tyust.course.manager.AppThemeCoordinator.INSTANCE.wrapContext(context));
    }
}
