package com.testapp.nativeAds;

import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.mopub.nativeads.MoPubNative;
import com.mopub.nativeads.MoPubStaticNativeAdRenderer;
import com.mopub.nativeads.NativeAd;
import com.mopub.nativeads.NativeErrorCode;
import com.mopub.nativeads.RequestParameters;
import com.mopub.nativeads.ViewBinder;
import com.testapp.R;

import java.util.EnumSet;
import java.util.Map;

/**
 * Created by Sven Steinert on 23.03.2018.
 */

public class NativeAdViewManager extends SimpleViewManager<View> implements View.OnAttachStateChangeListener, MoPubNative.MoPubNativeNetworkListener, NativeAd.MoPubNativeEventListener
{
    public static final String REACT_CLASS = "NativeTellAd";
    public static final String FAILURE_EVENT = "onFailure";
    public static final String SUCCESS_EVENT = "onSuccess";
    public static final String IMPRESSION_EVENT = "onImpression";
    public static final String CLICK_EVENT = "onClick";
    public static final int LAYOUT = R.layout.native_ad;

    private ThemedReactContext themedReactContext;
    private RCTEventEmitter emitter;

    private MoPubNative mopubNative;
    private NativeAd nativeAdHelper;
    private View adView;

    private String unitId;

    /**
     * This function is called when the View is going to be instantiated.
     * @param context The ThemedReactContext object.
     * @return The manually created View.
     */
    @Override
    public View createViewInstance(ThemedReactContext context)
    {
        themedReactContext = context;
        emitter = context.getJSModule(RCTEventEmitter.class);
        adView = LayoutInflater.from(context).inflate(LAYOUT, null);

        return adView;
    }

    /**
     * Creates a map of custom events.
     * @return A map of custom events.
     */
    @Override
    public Map<String, Object> getExportedCustomDirectEventTypeConstants() {
        MapBuilder.Builder<String, Object> builder = MapBuilder.builder();
        builder.put(FAILURE_EVENT, MapBuilder.of("registrationName", FAILURE_EVENT));
        builder.put(SUCCESS_EVENT, MapBuilder.of("registrationName", SUCCESS_EVENT));
        builder.put(IMPRESSION_EVENT, MapBuilder.of("registrationName", IMPRESSION_EVENT));
        builder.put(CLICK_EVENT, MapBuilder.of("registrationName", CLICK_EVENT));
        /* For developers
            builder.put(<attribut_name>, MapBuilder.of("registrationName", <attribut_name>));
         */
        return builder.build();
    }

    /**
     * This method is accessible by react-native and reloads the ad.
     */
    @ReactMethod
    public void reload()
    {
        RequestAdInternal();
    }

    /**
     * Requests a new ad.
     */
    private void RequestAdInternal()
    {
        if(unitId == null){
            return;
        }
        mopubNative = new MoPubNative(themedReactContext.getCurrentActivity(), unitId, this);
        mopubNative.registerAdRenderer(new MoPubStaticNativeAdRenderer(new ViewBinder.Builder(LAYOUT)
                            .titleId(R.id.ad_title)
                            .textId(R.id.ad_body)
                            .mainImageId(R.id.ad_image)
                            .build()));
        /* For developers
            You can add extra informations like:
            .addExtra("sponsoredtext", R.id.sponsored_text)
            .addExtra("sponsoredimage", R.id.sponsored_image)

            from: https://developers.mopub.com/docs/android/native/
         */

        mopubNative.makeRequest(new RequestParameters.Builder()
                .desiredAssets(EnumSet.of(
                        RequestParameters.NativeAdAsset.TITLE,
                        RequestParameters.NativeAdAsset.TEXT,
                        RequestParameters.NativeAdAsset.MAIN_IMAGE))
                .build());
    }

    /**
     * Sets the property "unitId"
     * @param view The view component.
     * @param unitId The ad unit Id.
     */
    @ReactProp(name = "unitId")
    public void setUnitId(View view, @Nullable String unitId) {
        this.unitId = unitId;
        RequestAdInternal();
    }

    /**
     * This function is called if the ad was loaded successfully.
     * @param nativeAd The native ad helper.
     */
    @Override
    public void onNativeLoad(NativeAd nativeAd) {
        this.nativeAdHelper = nativeAd;

        SendOnSuccess();
        nativeAd.clear(adView);
        nativeAd.setMoPubNativeEventListener(this);
        nativeAd.renderAdView(adView);
        nativeAd.prepare(adView);
    }

    /**
     * This function is called if the ad was not loaded.
     * @param errorCode The error message.
     */
    @Override
    public void onNativeFail(NativeErrorCode errorCode) {
        SendOnFailure(errorCode.toString());
        Log.w(this.getClass().getSimpleName(), errorCode.toString());
    }

    /**
     * This function is called when the ad view is rendered.
     * @param view The view which holds the ad content.
     */
    @Override
    public void onImpression(View view) {
        SendOnImpression();
    }

    /**
     * This function is called when the ad view was clicked.
     * @param view The view which holds the ad content.
     */
    @Override
    public void onClick(View view) {
        SendOnClick();
    }

    /**
     * Emits a react native event.
     */
    private void SendOnSuccess(){
        WritableMap event = Arguments.createMap();
        emitter.receiveEvent(adView.getId(), SUCCESS_EVENT, event);
    }

    /**
     * Emits a react native event.
     */
    private void SendOnFailure(String message){
        WritableMap event = Arguments.createMap();
        event.putString("message", message);
        emitter.receiveEvent(adView.getId(), FAILURE_EVENT, event);
    }

    /**
     * Emits a react native event.
     */
    private void SendOnImpression(){
        WritableMap event = Arguments.createMap();
        emitter.receiveEvent(adView.getId(), IMPRESSION_EVENT, event);
    }

    /**
     * Emits a react native event.
     */
    private void SendOnClick(){
        WritableMap event = Arguments.createMap();
        emitter.receiveEvent(adView.getId(), CLICK_EVENT, event);
    }

    @Override
    public void onViewAttachedToWindow(View v) { }

    /**
     * This function is called if the view was disposed.
     * Destorys the native ad helper and mopub native.
     * @param v The view which was disposed
     */
    @Override
    public void onViewDetachedFromWindow(View v) {
        if(nativeAdHelper != null){
            nativeAdHelper.destroy();
        }
        if(mopubNative != null){
            mopubNative.destroy();
        }
    }

    /**
     * Returns the name of the react class
     * @return
     */
    @Override
    public String getName() {
        return REACT_CLASS;
    }

}