package za.co.mjrsolutions.shared.permissions;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * Callback for {@link AgriPermissions#request} and friends.
 *
 * <p>Exactly one of {@link #onGranted()} or {@link #onDenied(List)} is invoked
 * on the main thread for every request.
 */
public interface PermissionCallback {

    /** All requested permissions are granted; caller may proceed. */
    void onGranted();

    /**
     * One or more permissions were denied.
     *
     * @param permanentlyDenied subset of manifest permission strings (e.g. {@code android.permission.CAMERA})
     *                          for which the user selected "Don't ask again". Empty list means the user
     *                          can be re-prompted. {@link Collections#emptyList()} is supplied when the
     *                          user dismissed the rationale dialog before the system prompt fired.
     */
    void onDenied(@NonNull List<String> permanentlyDenied);
}
