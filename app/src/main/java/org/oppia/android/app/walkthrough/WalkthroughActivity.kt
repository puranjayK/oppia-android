package org.oppia.android.app.walkthrough

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.oppia.android.app.activity.ActivityComponentImpl
import org.oppia.android.app.activity.InjectableAppCompatActivity
import javax.inject.Inject

/** Activity that contains the walkthrough flow for users. */
class WalkthroughActivity : InjectableAppCompatActivity(), WalkthroughFragmentChangeListener {
  @Inject
  lateinit var walkthroughActivityPresenter: WalkthroughActivityPresenter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (activityComponent as ActivityComponentImpl).inject(this)
    walkthroughActivityPresenter.handleOnCreate()
  }

  override fun currentPage(walkthroughPage: Int) {
    walkthroughActivityPresenter.changePage(walkthroughPage)
  }

  override fun pageWithTopicId(walkthroughPage: Int, topicId: String) {
    walkthroughActivityPresenter.setTopicId(topicId)
    walkthroughActivityPresenter.changePage(walkthroughPage)
  }

  override fun onBackPressed() {
    walkthroughActivityPresenter.handleSystemBack()
  }

  companion object {
    internal const val WALKTHROUGH_ACTIVITY_INTERNAL_PROFILE_ID_KEY =
      "WalkthroughActivity.internal_profile_id"

    fun createWalkthroughActivityIntent(context: Context, internalProfileId: Int): Intent {
      val intent = Intent(context, WalkthroughActivity::class.java)
      intent.putExtra(WALKTHROUGH_ACTIVITY_INTERNAL_PROFILE_ID_KEY, internalProfileId)
      return intent
    }
  }
}
