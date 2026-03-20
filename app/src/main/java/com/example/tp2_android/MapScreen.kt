package com.example.tp2_android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.content.res.Resources
import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker


private val BORDEAUX_CENTER = GeoPoint(44.8378, -0.5792)
private const val ZOOM_DEFAUT = 13.5

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: VeloViewModel,
    onMarkerClick: (V3Record) -> Unit = {}
) {
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var mapViewRef: MapView? by remember { mutableStateOf(null) }

    val locationPermission = rememberPermissionState(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    LaunchedEffect(userLocation) {
        val loc = userLocation ?: return@LaunchedEffect
        mapViewRef?.controller?.animateTo(
            GeoPoint(loc.latitude, loc.longitude),
            15.0, // zoom plus fort quand on se localise
            1200L // duree de l'animation en ms
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // La carte prend tout l'espace

        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(ZOOM_DEFAUT)
                    controller.setCenter(BORDEAUX_CENTER)
                    mapViewRef = this
                }
            },
            update = { mapView ->
                rafraichirMarqueurs(mapView, stations, userLocation, onMarkerClick)
            }, onRelease = { mapView ->
                mapView.onDetach()
            }
        )

        FloatingActionButton(
            onClick = {
                if (locationPermission.status.isGranted) {
                    viewModel.fetchUserLocation(context)
                } else {
                    locationPermission.launchPermissionRequest()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Me localiser")
        }
    }



}

/**
 * Cree une icone circulaire coloree pour un marqueur osmdroid.
 * @param couleurFond Couleur du disque principal (etat de la station)
 * @param couleurBord Couleur du contour blanc/gris pour le contraste
 * @param taillePx Diametre du cercle en pixels
 */
private fun creerIconeRonde(
    couleurFond: Int,
    couleurBord: Int = Color.WHITE,
    taillePx: Int = 48
): BitmapDrawable {
    val bitmap = Bitmap.createBitmap(taillePx, taillePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val rayon = taillePx / 2f
    // Contour blanc (legerement plus grand que le disque)
    val paintBord = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = couleurBord
        style = Paint.Style.FILL
    }
    canvas.drawCircle(rayon, rayon, rayon, paintBord)
    // Disque colore (80% du diametre)
    val paintFond = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = couleurFond
        style = Paint.Style.FILL
    }
    canvas.drawCircle(rayon, rayon, rayon * 0.80f, paintFond)
    return BitmapDrawable(Resources.getSystem(), bitmap)
}

private fun rafraichirMarqueurs(
    mapView: MapView,
    stations: List<V3Record>,
    userLocation: Location?,
    onMarkerClick: (V3Record) -> Unit
) {
    // 1. Supprimer tous les marqueurs existants
    mapView.overlays.clear()
    // 2. Ajouter un marqueur par station
    stations.forEach { record ->
        val geo = record.fields.geo_point_2d ?: return@forEach
        // geo[0] = latitude, geo[1] = longitude
        var position = GeoPoint(geo[0], geo[1])
        // Choix de la couleur selon l'etat
        val couleur = when {
            record.fields.etat != "CONNECTEE" -> Color.GRAY // hors service
            record.fields.nbvelos == 0 -> Color.RED // connectee mais vide
                    else -> Color.parseColor("#2E7D32") // OK (vert)
        }
        val marker = Marker(mapView).apply {
            position = position
            title = record.fields.nom
            snippet = buildSnippet(record.fields)
            icon = creerIconeRonde(couleur)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            // Clic sur le marqueur -> ouvre DetailScreen
            setOnMarkerClickListener { _, _ ->
                onMarkerClick(record)
                true
            }
        }
        mapView.overlays.add(marker)
    }
    userLocation?.let { loc ->
        val moi = Marker(mapView).apply {
            position = GeoPoint(loc.latitude, loc.longitude)
            title = "Ma position"
            icon = creerIconeRonde(Color.parseColor("#1565C0"), Color.WHITE, 40)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
        mapView.overlays.add(moi)
    }
    mapView.invalidate()
}
private fun buildSnippet(fields: V3Fields): String {
    val etatStr = if (fields.etat == "CONNECTEE") "Connectee" else "Hors service"
    return "${fields.nbvelos} velos | ${fields.nbplaces} places | $etatStr"
}