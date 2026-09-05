package sk.ziacik.androidstreamplayer.subtitle

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.junit.Test

class PodnapisiXmlFactoryTest {
	@Test
	fun `xml parser configuration tolerates Android XInclude unsupported`() {
		configurePodnapisiXmlFactory(XIncludeUnsupportedFactory())
	}

	private class XIncludeUnsupportedFactory : DocumentBuilderFactory() {
		private val delegate = newInstance()

		override fun setXIncludeAware(state: Boolean) {
			throw UnsupportedOperationException("Android does not support XInclude configuration")
		}

		@Throws(ParserConfigurationException::class)
		override fun newDocumentBuilder(): DocumentBuilder = delegate.newDocumentBuilder()

		override fun setAttribute(name: String, value: Any) = delegate.setAttribute(name, value)

		override fun getAttribute(name: String): Any = delegate.getAttribute(name)

		@Throws(ParserConfigurationException::class)
		override fun setFeature(name: String, value: Boolean) = delegate.setFeature(name, value)

		@Throws(ParserConfigurationException::class)
		override fun getFeature(name: String): Boolean = delegate.getFeature(name)
	}
}
