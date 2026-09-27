package com.taxidriver.apk;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    // Google Play product IDs configured in Play Console.
    private static final String LIFETIME_PRODUCT_ID = "taxi_driver_premium";
    private static final String MONTHLY_PRODUCT_ID = "taxi_driver_premium_monthly";
    private static final String YEARLY_PRODUCT_ID = "taxi_driver_premium_yearly";

    // Google test banner ID already present in the project.
    private static final String TEST_BANNER_AD_UNIT_ID = "ca-app-pub-2119693628616724/6269224943";

    private WebView webView;
    private AdView adView;
    private BillingClient billingClient;

    private ProductDetails lifetimeProductDetails;
    private ProductDetails monthlyProductDetails;
    private ProductDetails yearlyProductDetails;
    private boolean premiumActive = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        adView = new AdView(this);
        adView.setAdUnitId(TEST_BANNER_AD_UNIT_ID);
        adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, 360));
        FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        adParams.gravity = android.view.Gravity.BOTTOM;
        root.addView(adView, adParams);
        setContentView(root);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AndroidBillingBridge(), "AndroidBilling");
        webView.loadUrl("file:///android_asset/index.html");

        MobileAds.initialize(this, status -> {});
        loadAdsIfNeeded();
        setupBilling();
    }

    private void setupBilling() {
        PendingPurchasesParams pendingParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build();

        billingClient = BillingClient.newBuilder(this)
                .setListener(this::onPurchasesUpdated)
                .enablePendingPurchases(pendingParams)
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryExistingPurchases();
                    queryAllPremiumProducts();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // A later user action will retry the connection.
            }
        });
    }

    private void queryAllPremiumProducts() {
        queryAllPremiumProducts(null);
    }

    private void queryAllPremiumProducts(Runnable onComplete) {
        if (billingClient == null || !billingClient.isReady()) {
            if (onComplete != null) runOnUiThread(onComplete);
            return;
        }

        final boolean[] lifetimeDone = {false};
        final boolean[] subscriptionsDone = {false};
        final boolean[] completed = {false};

        Runnable finish = () -> {
            if (!completed[0] && lifetimeDone[0] && subscriptionsDone[0]) {
                completed[0] = true;
                if (onComplete != null) runOnUiThread(onComplete);
            }
        };

        QueryProductDetailsParams inAppParams = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(LIFETIME_PRODUCT_ID)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()))
                .build();

        billingClient.queryProductDetailsAsync(inAppParams, (result, detailsResult) -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && detailsResult != null
                    && detailsResult.getProductDetailsList() != null
                    && !detailsResult.getProductDetailsList().isEmpty()) {
                lifetimeProductDetails = detailsResult.getProductDetailsList().get(0);
            }
            lifetimeDone[0] = true;
            finish.run();
        });

        List<QueryProductDetailsParams.Product> subscriptionProducts = new ArrayList<>();
        subscriptionProducts.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(MONTHLY_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build());
        subscriptionProducts.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(YEARLY_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build());

        QueryProductDetailsParams subsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(subscriptionProducts)
                .build();

        billingClient.queryProductDetailsAsync(subsParams, (result, detailsResult) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK
                    || detailsResult == null
                    || detailsResult.getProductDetailsList() == null) {
                subscriptionsDone[0] = true;
                finish.run();
                return;
            }

            for (ProductDetails details : detailsResult.getProductDetailsList()) {
                if (MONTHLY_PRODUCT_ID.equals(details.getProductId())) {
                    monthlyProductDetails = details;
                } else if (YEARLY_PRODUCT_ID.equals(details.getProductId())) {
                    yearlyProductDetails = details;
                }
            }
            subscriptionsDone[0] = true;
            finish.run();
        });
    }

    private void queryExistingPurchases() {
        if (billingClient == null || !billingClient.isReady()) return;

        QueryPurchasesParams inAppParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        billingClient.queryPurchasesAsync(inAppParams, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                for (Purchase purchase : purchases) {
                    if (purchase.getProducts().contains(LIFETIME_PRODUCT_ID)
                            && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                        acknowledgeIfNeeded(purchase);
                        return;
                    }
                }
            }
            querySubscriptionPurchases();
        });
    }

    private void querySubscriptionPurchases() {
        if (billingClient == null || !billingClient.isReady()) return;

        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                setPremiumState(false);
                return;
            }

            boolean found = false;
            for (Purchase purchase : purchases) {
                boolean isOurSubscription =
                        purchase.getProducts().contains(MONTHLY_PRODUCT_ID)
                                || purchase.getProducts().contains(YEARLY_PRODUCT_ID);

                if (isOurSubscription
                        && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    found = true;
                    acknowledgeIfNeeded(purchase);
                    break;
                }
            }

            if (!found) setPremiumState(false);
        });
    }

    private void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                && purchases != null) {
            for (Purchase purchase : purchases) {
                boolean isOurProduct =
                        purchase.getProducts().contains(LIFETIME_PRODUCT_ID)
                                || purchase.getProducts().contains(MONTHLY_PRODUCT_ID)
                                || purchase.getProducts().contains(YEARLY_PRODUCT_ID);

                if (isOurProduct
                        && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    acknowledgeIfNeeded(purchase);
                }
            }
        } else if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(this, "Premium purchase could not be completed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void acknowledgeIfNeeded(Purchase purchase) {
        if (purchase.isAcknowledged()) {
            setPremiumState(true);
            return;
        }

        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        billingClient.acknowledgePurchase(params, billingResult -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                setPremiumState(true);
            }
        });
    }

    private void launchPremiumPurchase() {
        if (billingClient == null) {
            setupBilling();
            Toast.makeText(this, "Connecting to Google Play...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!billingClient.isReady()) {
            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(@NonNull BillingResult result) {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        queryAllPremiumProducts(this::showPremiumPlans);
                        queryExistingPurchases();
                    }
                }

                @Override
                public void onBillingServiceDisconnected() {}
            });
            return;
        }

        if (premiumActive) return;

        queryAllPremiumProducts(this::showPremiumPlans);
    }

    private void showPremiumPlans() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;

            String lifetime = getOneTimePrice(lifetimeProductDetails);
            String monthly = getSubscriptionPrice(monthlyProductDetails);
            String yearly = getSubscriptionPrice(yearlyProductDetails);

            if (lifetime == null && monthly == null && yearly == null) {
                Toast.makeText(this,
                        "Premium plans are not available yet. Please try again.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            final int navy = Color.rgb(7, 25, 48);
            final int gold = Color.rgb(218, 165, 32);
            final int dark = Color.rgb(20, 20, 20);
            final int light = Color.rgb(248, 249, 252);

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(0), dp(0), dp(0), dp(8));
            root.setBackground(roundBg(Color.WHITE, 28, Color.TRANSPARENT, 0));

            // Premium header
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.VERTICAL);
            header.setGravity(Gravity.CENTER_HORIZONTAL);
            header.setPadding(dp(18), dp(18), dp(18), dp(18));
            header.setBackground(roundBg(navy, 28, navy, 0));

            TextView crown = new TextView(this);
            crown.setText("👑");
            crown.setTextSize(40);
            crown.setGravity(Gravity.CENTER);
            header.addView(crown, new LinearLayout.LayoutParams(-1, dp(52)));

            TextView title = new TextView(this);
            title.setText("Taxi Driver");
            title.setTextColor(Color.WHITE);
            title.setTextSize(27);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            header.addView(title);

            TextView premium = new TextView(this);
            premium.setText("Premium");
            premium.setTextColor(Color.rgb(255, 207, 70));
            premium.setTextSize(34);
            premium.setTypeface(null, android.graphics.Typeface.BOLD);
            premium.setGravity(Gravity.CENTER);
            header.addView(premium);

            TextView taxi = new TextView(this);
            taxi.setText("🚕");
            taxi.setTextSize(62);
            taxi.setGravity(Gravity.CENTER);
            taxi.setPadding(0, dp(4), 0, dp(4));
            header.addView(taxi, new LinearLayout.LayoutParams(-1, dp(82)));

            TextView benefits = new TextView(this);
            benefits.setText("▥  Full Features     🚫  No Ads     ☁  Use Everywhere");
            benefits.setTextColor(Color.WHITE);
            benefits.setTextSize(13);
            benefits.setGravity(Gravity.CENTER);
            header.addView(benefits, new LinearLayout.LayoutParams(-1, dp(42)));

            root.addView(header, new LinearLayout.LayoutParams(-1, dp(275)));

            TextView plansTitle = new TextView(this);
            plansTitle.setText("Choose your Premium plan");
            plansTitle.setTextColor(dark);
            plansTitle.setTextSize(16);
            plansTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            plansTitle.setPadding(dp(20), dp(16), dp(20), dp(8));
            root.addView(plansTitle);

            LinearLayout plans = new LinearLayout(this);
            plans.setOrientation(LinearLayout.VERTICAL);
            plans.setPadding(dp(14), 0, dp(14), dp(4));

            final android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
            final int[] selected = {0};

            if (lifetime != null) {
                RadioButton lifetimeRow = premiumPlanRow("💎", "Lifetime", lifetime, true, gold, light);
                lifetimeRow.setOnClickListener(v -> {
                    selected[0] = 0;
                    lifetimeRow.setChecked(true);
                });
                plans.addView(lifetimeRow, new LinearLayout.LayoutParams(-1, dp(70)));
            }

            if (monthly != null) {
                RadioButton monthlyRow = premiumPlanRow("📅", "Monthly", monthly, false, Color.rgb(55, 130, 230), Color.WHITE);
                monthlyRow.setOnClickListener(v -> {
                    selected[0] = 1;
                    monthlyRow.setChecked(true);
                });
                plans.addView(monthlyRow, new LinearLayout.LayoutParams(-1, dp(70)));
            }

            if (yearly != null) {
                RadioButton yearlyRow = premiumPlanRow("📆", "Yearly", yearly, false, Color.rgb(40, 180, 120), Color.WHITE);
                yearlyRow.setOnClickListener(v -> {
                    selected[0] = 2;
                    yearlyRow.setChecked(true);
                });
                plans.addView(yearlyRow, new LinearLayout.LayoutParams(-1, dp(70)));
            }

            root.addView(plans);

            Button cancel = new Button(this);
            cancel.setText("CANCEL");
            cancel.setTextColor(gold);
            cancel.setTextSize(15);
            cancel.setTypeface(null, android.graphics.Typeface.BOLD);
            cancel.setAllCaps(false);
            cancel.setBackground(roundBg(Color.TRANSPARENT, 20, gold, dp(1)));
            cancel.setOnClickListener(v -> {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });

            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, dp(54));
            cancelParams.setMargins(dp(14), dp(8), dp(14), dp(8));
            root.addView(cancel, cancelParams);

            // A single hidden selection is not used for the purchase itself; tapping a plan purchases it directly.
            android.app.AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(root)
                    .create();
            dialogRef[0] = dialog;
            dialog.setOnShowListener(d -> {
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                    dialog.getWindow().setDimAmount(0.65f);
                    dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                }
            });

            // Replace row click listeners with purchase actions after dialog is created.
            if (lifetime != null) {
                View row = plans.getChildAt(0);
                row.setOnClickListener(v -> {
                    selected[0] = 0;
                    launchOneTimePurchase();
                    dialog.dismiss();
                });
            }
            int index = lifetime != null ? 1 : 0;
            if (monthly != null) {
                View row = plans.getChildAt(index++);
                row.setOnClickListener(v -> {
                    selected[0] = 1;
                    launchSubscriptionPurchase(monthlyProductDetails);
                    dialog.dismiss();
                });
            }
            if (yearly != null) {
                View row = plans.getChildAt(index);
                row.setOnClickListener(v -> {
                    selected[0] = 2;
                    launchSubscriptionPurchase(yearlyProductDetails);
                    dialog.dismiss();
                });
            }

            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        (int) (getResources().getDisplayMetrics().widthPixels * 0.90f),
                        android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
    }

    private RadioButton premiumPlanRow(String icon, String name, String price,
                                       boolean checked, int accent, int background) {
        RadioButton row = new RadioButton(this);
        row.setText(icon + "    " + name + "                                      " + price);
        row.setTextColor(Color.rgb(20, 20, 20));
        row.setTextSize(17);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTypeface(null, android.graphics.Typeface.BOLD);
        row.setChecked(checked);
        row.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{accent, Color.GRAY}));
        row.setPadding(dp(12), 0, dp(8), 0);
        row.setBackground(roundBg(background, 22, accent, checked ? dp(1) : 0));
        return row;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundBg(int color, int radiusDp, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private String getOneTimePrice(ProductDetails details) {
        if (details == null || details.getOneTimePurchaseOfferDetailsList() == null
                || details.getOneTimePurchaseOfferDetailsList().isEmpty()) return null;
        return details.getOneTimePurchaseOfferDetailsList().get(0).getFormattedPrice();
    }

    private String getSubscriptionPrice(ProductDetails details) {
        if (details == null || details.getSubscriptionOfferDetails() == null
                || details.getSubscriptionOfferDetails().isEmpty()) return null;

        ProductDetails.SubscriptionOfferDetails offer =
                details.getSubscriptionOfferDetails().get(0);

        if (offer.getPricingPhases() == null
                || offer.getPricingPhases().getPricingPhaseList().isEmpty()) return null;

        return offer.getPricingPhases().getPricingPhaseList().get(0).getFormattedPrice();
    }

    private String getSubscriptionOfferToken(ProductDetails details) {
        if (details == null || details.getSubscriptionOfferDetails() == null
                || details.getSubscriptionOfferDetails().isEmpty()) return null;

        return details.getSubscriptionOfferDetails().get(0).getOfferToken();
    }

    private void launchOneTimePurchase() {
        if (lifetimeProductDetails == null
                || lifetimeProductDetails.getOneTimePurchaseOfferDetailsList() == null
                || lifetimeProductDetails.getOneTimePurchaseOfferDetailsList().isEmpty()) {
            Toast.makeText(this, "Lifetime Premium is not available yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        String offerToken = lifetimeProductDetails.getOneTimePurchaseOfferDetailsList()
                .get(0).getOfferToken();

        BillingFlowParams.ProductDetailsParams productParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(lifetimeProductDetails)
                        .setOfferToken(offerToken)
                        .build();

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productParams))
                .build();

        billingClient.launchBillingFlow(this, flowParams);
    }

    private void launchSubscriptionPurchase(ProductDetails details) {
        if (details == null) {
            Toast.makeText(this, "This subscription is not available yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        String offerToken = getSubscriptionOfferToken(details);
        if (offerToken == null) {
            Toast.makeText(this, "Subscription offer is not available yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        BillingFlowParams.ProductDetailsParams productParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build();

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productParams))
                .build();

        billingClient.launchBillingFlow(this, flowParams);
    }

    private void setPremiumState(boolean active) {
        premiumActive = active;
        runOnUiThread(() -> {
            if (active) {
                if (adView != null) adView.setVisibility(View.GONE);
            } else {
                loadAdsIfNeeded();
            }

            if (webView != null) {
                webView.evaluateJavascript(
                        "window.AndroidPremiumState && window.AndroidPremiumState(" + active + ");",
                        null);
            }
        });
    }

    private void loadAdsIfNeeded() {
        if (premiumActive || adView == null) return;
        adView.setVisibility(View.VISIBLE);
        if (adView.getAdSize() != null) {
            adView.loadAd(new AdRequest.Builder().build());
        }
    }

    private class AndroidBillingBridge {
        @JavascriptInterface
        public void purchasePremium() {
            runOnUiThread(() -> launchPremiumPurchase());
        }

        @JavascriptInterface
        public boolean isPremium() {
            return premiumActive;
        }

        @JavascriptInterface
        public void restorePurchases() {
            runOnUiThread(() -> {
                queryExistingPurchases();
                Toast.makeText(MainActivity.this,
                        "Checking Google Play purchases...",
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (billingClient != null && billingClient.isReady()) {
            queryExistingPurchases();
            queryAllPremiumProducts();
        }
        if (!premiumActive) loadAdsIfNeeded();
    }

    @Override
    protected void onDestroy() {
        if (adView != null) adView.destroy();
        if (billingClient != null) billingClient.endConnection();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
