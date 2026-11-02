# v1.1.0.7 Beta — account-only Home and aligned recommendation Shorts

- Both native layouts now use the verified signed-in Google Home feed exclusively.
  Removed the disconnected Home path that displayed local ranking, generic discovery
  shelves or a default science/technology search. When not signed in, Home shows the
  existing Google sign-in connection instead of presenting generic results as yours.
- Home parser excludes sidebar/menu/ad/unselected-tab content. Recognized cards are
  atomic, preventing nested action menus from becoming extra recommendations. Modern
  lockups must explicitly identify video content; playlist/channel lockups are not videos.
- Consecutive source-proven Shorts share aligned two-column portrait rows. Thumbnail
  and title widths now match; two-line title areas align; an unpaired Short retains a
  half-width column. Ordinary videos stay full-width landscape. Google's returned order
  is retained instead of collecting all Shorts above the remaining recommendations.
- Late source-proven Shorts corrections are applied when scrolling is idle, without
  eagerly looking up every offscreen recommendation.
- Search/topic discovery remains available explicitly, not as account Home fallback.
  Google's history pause and recommendation choices are not overwritten or fabricated.

Google controls its recommendation algorithm: this change prevents incorrect source
mixing, not a promise that Google itself will always return relevant recommendations
or pixel-identical feeds across clients. Live account/physical-device verification
is not claimed. Liked Music and independent background switches are unchanged.
