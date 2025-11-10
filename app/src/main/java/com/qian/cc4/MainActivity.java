package com.qian.cc4;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigation;
    private ViewPagerAdapter adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setupViewPager();
        setupBottomNavigation();
    }
    
    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        bottomNavigation = findViewById(R.id.bottomNavigation);
    }
    
    private void setupViewPager() {
        List<BaseFragment> fragments = new ArrayList<>();
        fragments.add(new CCTestFragment()); //cctest
        fragments.add(new DdosTestFragment()); //ddostest
        fragments.add(new AboutFragment()); //aboutfragment
        
        adapter = new ViewPagerAdapter(this, fragments);
        viewPager.setAdapter(adapter);
        
        viewPager.setUserInputEnabled(true);
    }
    
    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_cc_test) {
                viewPager.setCurrentItem(0, false);
                return true;
            } else if (item.getItemId() == R.id.nav_ddos_test) {
                viewPager.setCurrentItem(1, false);
                return true;
            } else if (item.getItemId() == R.id.nav_about) {
                viewPager.setCurrentItem(2, false);
                return true;
            }
            return false;
        });
    }
}
